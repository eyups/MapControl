// HAB camera emulator shim (emulator-only, LD_PRELOAD).
// App's libroaddeck_hab.so talks to /dev/hab with Desay qcarcam ioctls.
// This shim fakes that device+server and feeds YUYV frames from a looped file
// (or a synthetic pattern) into the caller's exported ION buffers.
#define _GNU_SOURCE
#include <dlfcn.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>
#include <android/log.h>

#include <map>
#include <mutex>
#include <string>
#include <vector>

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "HabShim", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "HabShim", __VA_ARGS__)

// ---- HAB ioctls (must match libroaddeck_hab) ----
static constexpr unsigned long kIoctlOpen     = 0xC0100A04UL;
static constexpr unsigned long kIoctlSend     = 0x40180A02UL;
static constexpr unsigned long kIoctlRecv     = 0xC0180A03UL;
static constexpr unsigned long kIoctlClose    = 0x40080A05UL;
static constexpr unsigned long kIoctlExport   = 0xC0180A06UL;
static constexpr unsigned long kIoctlUnexport = 0x400C0A08UL;

struct HabOpenReq { uint32_t id, sub, neg, zero; };
struct HabSendReq { const void* buf; uint32_t chan, size; uint64_t reserved; };
struct HabRecvReq { void* buf; uint32_t chan, size; uint64_t timeout; };
struct HabCloseReq { uint32_t chan, zero; };
struct HabExportReq { void* ptr; uint32_t chan, size; uint64_t handle; };
struct HabUnexportReq { uint32_t chan, handle, one; };

// ---- state ----
struct Buffer { void* ptr = nullptr; uint32_t size = 0; uint32_t handle = 0; };
struct Stream {
    uint32_t inputId = 0;
    uint32_t sourceIndex = 0;   // 0..3: per-camera file index (front/right/left/rear)
    uint64_t token = 0;
    uint32_t width = 1280, height = 800, stride = 2560, format = 0x07080100;
    uint32_t frameSize = 1280 * 800 * 2;
    uint32_t expectedAcks = 0, acks = 0;
    bool started = false;
    std::vector<Buffer> bufs;
    uint32_t nextIdx = 0;
};
struct Channel { std::vector<std::vector<uint8_t>> q; };
struct FdState {
    int efd = -1;                       // real pollable fd handed to the app
    uint32_t session = 0;
    uint32_t chanHs = 0, chanA = 0, chanB = 0;
    uint32_t nextChan = 1;
    std::map<uint32_t, Channel> chans;  // chan id -> queued replies
    std::map<uint64_t, Stream> streams;
    uint64_t nextToken = 0x51A0000000000000ULL;
    uint32_t nextHandle = 1;
};

static std::mutex gLock;
static std::map<int, FdState*> gFds;    // app fd -> state
static std::map<int, bool> gIonFds;     // fake /dev/ion fds
static constexpr unsigned long kIonIoctlAlloc = 0xC0184900UL;
struct IonAllocReq { uint64_t len; uint32_t heapMask, flags; int32_t fd; uint32_t pad; };
static uint32_t gNextSession = 0x0C0A0000u;

static int (*real_open)(const char*, int, ...) = nullptr;
static int (*real_openat)(int, const char*, int, ...) = nullptr;
static int (*real_close)(int) = nullptr;
static int (*real_ioctl)(int, int, ...) = nullptr;

// ---- synthetic / file frame source ----
static std::mutex gFrameLock;
static uint8_t* gFileData[4] = {nullptr, nullptr, nullptr, nullptr};
static size_t gFileSize[4] = {0, 0, 0, 0};
static bool gFileTried[4] = {false, false, false, false};
static uint32_t gFrameCounter = 0;

static int64_t nowMs() {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

// Load /data/local/tmp/habcam/cam<N>.yuyv or frame.yuyv (looped).
static void ensureFile(int idx) {
    if (gFileTried[idx]) return;
    gFileTried[idx] = true;
    const char* paths[3] = {nullptr, nullptr, nullptr};
    char p1[128], p2[128];
    snprintf(p1, sizeof(p1), "/data/local/tmp/habcam/cam%d.yuyv", idx);
    snprintf(p2, sizeof(p2), "/data/local/tmp/habcam/frame.yuyv");
    paths[0] = p1; paths[1] = p2;
    for (int i = 0; i < 2; i++) {
        int fd = real_open(paths[i], O_RDONLY | O_CLOEXEC);
        if (fd < 0) continue;
        struct stat st{};
        if (fstat(fd, &st) == 0 && st.st_size > 0) {
            void* m = mmap(nullptr, st.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
            if (m != MAP_FAILED) {
                gFileData[idx] = (uint8_t*)m;
                gFileSize[idx] = (size_t)st.st_size;
                LOG("frame source cam%d: %s (%zu bytes)", idx, paths[i], gFileSize[idx]);
                real_close(fd);
                return;
            }
        }
        real_close(fd);
    }
    LOG("frame source cam%d: synthetic", idx);
}

static int sourceIndexForInput(uint32_t inputId) {
    // Araç HAB kamera id'leri: 8=ön, 5=sağ, 9=sol, 4=arka.
    switch (inputId) {
        case 8: return 0;
        case 5: return 1;
        case 9: return 2;
        case 4: return 3;
        default: return (int)(inputId % 4);
    }
}

static inline void putYuyv(uint8_t* row, int x, int Y, int U, int V) {
    size_t o = (size_t)x * 2;
    row[o] = (uint8_t)Y; row[o + 1] = (uint8_t)U;
    row[o + 2] = (uint8_t)Y; row[o + 3] = (uint8_t)V;
}

// Fill one YUYV frame into dst (w*h*2).
static void fillFrame(int idx, uint8_t* dst, uint32_t w, uint32_t h) {
    ensureFile(idx);
    if (gFileData[idx] && gFileSize[idx] >= (size_t)w * h * 2) {
        size_t frames = gFileSize[idx] / ((size_t)w * h * 2);
        size_t fi = frames ? (gFrameCounter++ % frames) : 0;
        memcpy(dst, gFileData[idx] + fi * (size_t)w * h * 2, (size_t)w * h * 2);
        return;
    }
    // Synthetic: 4 tinted bars + moving block + per-camera tint.
    static const uint8_t kY[4] = {180, 160, 140, 120};
    static const uint8_t kU[4] = {90, 160, 60, 200};
    static const uint8_t kV[4] = {200, 60, 130, 90};
    uint32_t t = (uint32_t)(nowMs() / 40);
    int bx = (int)((t * 7) % (w > 160 ? w - 160 : 1));
    int by = (int)(h / 2 - 40);
    for (uint32_t y = 0; y < h; y += 2) {
        uint8_t* row = dst + (size_t)y * w * 2;
        for (uint32_t x = 0; x < w; x += 2) {
            bool block = (int)x >= bx && (int)x < bx + 160 && (int)y >= by && (int)y < by + 80;
            int Y = block ? 235 : (kY[idx] + (int)(30 * y / (h ? h : 1)));
            int U = block ? 128 : kU[idx];
            int V = block ? 128 : kV[idx];
            putYuyv(row, x, Y, U, V);
        }
        if (y + 1 < h) memcpy(dst + (size_t)(y + 1) * w * 2, row, (size_t)w * 2);
    }
}

// ---- helpers (call with gLock held) ----
static void pushReply(FdState* st, uint32_t chan, const void* data, size_t len) {
    auto it = st->chans.find(chan);
    if (it == st->chans.end()) return;
    it->second.q.emplace_back((const uint8_t*)data, (const uint8_t*)data + len);
    uint64_t one = 1;
    if (write(st->efd, &one, sizeof(one)) < 0) { /* ignore */ }
}

static void pushZeros(FdState* st, uint32_t chan, size_t len) {
    std::vector<uint8_t> z(len, 0);
    pushReply(st, chan, z.data(), z.size());
}

static Stream* streamFor(FdState* st, uint64_t token) {
    auto it = st->streams.find(token);
    return it == st->streams.end() ? nullptr : &it->second;
}

static void handleSend(FdState* st, uint32_t chan, const uint8_t* m, uint32_t size) {
    if (size < 4) return;
    uint64_t type64 = 0;
    if (size >= 8) memcpy(&type64, m, 8);
    uint32_t type32 = 0; memcpy(&type32, m, 4);

    // handshake channel
    if (chan == st->chanHs) {
        uint8_t r[8] = {};
        memcpy(r, &st->session, 4);
        pushReply(st, chan, r, sizeof(r));
        return;
    }
    if (chan != st->chanA && chan != st->chanB) return;

    if (chan == st->chanB) {
        static int t11 = 0;
        if (t11 < 12) { t11++; LOGW("tip11 #%d size=%u type=%u token=0x%llx streams=%zu", t11, size, type32, (unsigned long long)(size>=16?*(uint64_t*)(m+8):0), st->streams.size()); }
        if (type32 != 11 || size < 0x28) return;
        uint64_t token = 0; memcpy(&token, m + 8, 8);
        Stream* s = streamFor(st, token);
        if (!s || s->bufs.empty()) {
            LOGW("grab: no %s token=0x%llx", s ? "bufs" : "stream", (unsigned long long)token);
            uint8_t r[80] = {};
            uint32_t st2 = 1; memcpy(r + 4, &st2, 4);
            pushReply(st, chan, r, sizeof(r));
            return;
        }
        uint32_t idx = s->nextIdx % (uint32_t)s->bufs.size();
        s->nextIdx++;
        Buffer& b = s->bufs[idx];
        if (b.ptr && b.size >= s->frameSize) {
            std::lock_guard<std::mutex> fl(gFrameLock);
            fillFrame(sourceIndexForInput(s->inputId), (uint8_t*)b.ptr, s->width, s->height);
        }
        static int logged = 0;
        if (logged < 5) { logged++; LOG("grab ok cam%d idx%u token=0x%llx", s->inputId, idx, (unsigned long long)token); }
        uint8_t r[80] = {};
        uint32_t status = 0; memcpy(r + 4, &status, 4);
        memcpy(r + 0x20, &idx, 4);
        pushReply(st, chan, r, sizeof(r));
        return;
    }

    // chanA messages
    if (type64 == 17 || type64 == 19) { pushZeros(st, chan, 8); return; }
    if (type64 == 7 && size >= 16) {
        uint64_t token = 0; memcpy(&token, m + 8, 8);
        if (Stream* s = streamFor(st, token)) s->started = true;
        pushZeros(st, chan, 16);
        return;
    }
    if (type64 == 8 && size >= 16) { pushZeros(st, chan, 16); return; }
    if (type64 == 2 && size >= 16) { pushZeros(st, chan, 16); return; }
    if (type64 == 12 && size >= 24) { pushZeros(st, chan, 24); return; }

    if (type32 == 0) {                       // input list query -> empty list
        pushZeros(st, chan, 4);
        return;
    }
    if (type32 == 1 && size >= 24) {         // cam open: token at +16
        uint32_t inputId = 0; memcpy(&inputId, m + 8, 4);
        Stream s;
        s.inputId = inputId;
        s.sourceIndex = (uint32_t)st->streams.size();
        s.token = st->nextToken++;
        st->streams[s.token] = s;
        uint8_t r[24] = {};
        memcpy(r + 16, &st->streams[s.token].token, 8);
        pushReply(st, chan, r, sizeof(r));
        return;
    }
    if (type32 == 5 && size >= 0x28) {       // config: token@8, format@0x10, count@0x20, entries@0x28
        uint64_t token = 0; memcpy(&token, m + 8, 8);
        Stream* s = streamFor(st, token);
        if (!s) return;
        memcpy(&s->format, m + 0x10, 4);
        uint32_t count = 0; memcpy(&count, m + 0x20, 4);
        if (count > 8) count = 8;
        s->expectedAcks = count;
        s->acks = 0;
        s->bufs.assign(count, Buffer());
        if (count > 0 && size >= 0x28 + count * 0x50) {
            memcpy(&s->width, m + 0x28 + 0x00, 4);
            memcpy(&s->height, m + 0x28 + 0x04, 4);
            memcpy(&s->stride, m + 0x28 + 0x08, 4);
            memcpy(&s->frameSize, m + 0x28 + 0x0C, 4);
        }
        if (s->width < 2 || s->height < 2 || s->frameSize < 4) {
            s->width = 1280; s->height = 800; s->stride = 2560; s->frameSize = 1280 * 800 * 2;
        }
        LOG("cam%d config %ux%u stride%u frame%u bufs%u fmt0x%08x", s->inputId, s->width,
            s->height, s->stride, s->frameSize, count, s->format);
        pushZeros(st, chan, 4);              // server buffer count
        return;
    }
    if (size == 8) {                          // export ack (handle)
        if (st->streams.size() == 1) {
            Stream& s = st->streams.begin()->second;
            if (s.acks < s.expectedAcks) s.acks++;
            if (s.acks == s.expectedAcks) pushZeros(st, chan, 8);
        }
        return;
    }
}

// ---- ioctl emulation ----
static int fakeIoctl(FdState* st, unsigned long req, void* arg) {
    std::lock_guard<std::mutex> g(gLock);
    switch (req) {
        case kIoctlOpen: {
            HabOpenReq* r = (HabOpenReq*)arg;
            const uint32_t sub = r->sub >> 16;
            uint32_t chan = st->nextChan++;
            st->chans.emplace(chan, Channel());
            if (sub == 0) st->chanHs = chan;
            else if (st->chanA == 0) st->chanA = chan;
            else st->chanB = chan;
            r->id = chan;
            return 0;
        }
        case kIoctlSend: {
            HabSendReq* r = (HabSendReq*)arg;
            handleSend(st, r->chan, (const uint8_t*)r->buf, r->size);
            return 0;
        }
        case kIoctlRecv: {
            HabRecvReq* r = (HabRecvReq*)arg;
            auto it = st->chans.find(r->chan);
            if (it == st->chans.end() || it->second.q.empty()) {
                errno = EAGAIN;
                return -1;
            }
            std::vector<uint8_t>& v = it->second.q.front();
            uint32_t n = (uint32_t)(v.size() < r->size ? v.size() : r->size);
            memcpy(r->buf, v.data(), n);
            r->size = n;
            it->second.q.erase(it->second.q.begin());
            if (it->second.q.empty()) {
                uint64_t one = 0;
                read(st->efd, &one, sizeof(one));   // drain readiness
            }
            return 0;
        }
        case kIoctlClose: {
            HabCloseReq* r = (HabCloseReq*)arg;
            auto it = st->chans.find(r->chan);
            if (it != st->chans.end()) st->chans.erase(it);
            return 0;
        }
        case kIoctlExport: {
            HabExportReq* r = (HabExportReq*)arg;
            uint32_t handle = st->nextHandle++;
            r->handle = handle;
            if (st->streams.size() == 1) {
                Stream& s = st->streams.begin()->second;
                for (auto& b : s.bufs) {
                    if (b.ptr == nullptr) {
                        b.ptr = r->ptr;
                        b.size = r->size;
                        b.handle = handle;
                        break;
                    }
                }
            }
            return 0;
        }
        case kIoctlUnexport:
            return 0;
        default:
            return 0;
    }
}

static int newIonFd() {
    int efd = eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC);
    if (efd < 0) return -1;
    { std::lock_guard<std::mutex> g(gLock); gIonFds[efd] = true; }
    LOG("open /dev/ion -> fake fd %d", efd);
    return efd;
}

static bool isHabPath(const char* p) {
    return p && (strcmp(p, "/dev/hab") == 0 || strcmp(p, "/dev/hab0") == 0);
}

static int newHabFd() {
    int efd = eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC);
    if (efd < 0) return -1;
    FdState* st = new FdState();
    st->efd = efd;
    {
        std::lock_guard<std::mutex> g(gLock);
        st->session = gNextSession++;
        gFds[efd] = st;
    }
    LOG("open /dev/hab -> fake fd %d (session 0x%x)", efd, st->session);
    return efd;
}

extern "C" int __open_2(const char* path, int flags) {
    static int (*real___open_2)(const char*, int) = nullptr;
    if (!real___open_2) real___open_2 = (decltype(real___open_2))dlsym(RTLD_NEXT, "__open_2");
    if (isHabPath(path)) return newHabFd();
    if (path && strcmp(path, "/dev/ion") == 0) return newIonFd();
    return real___open_2(path, flags);
}

extern "C" int __openat_2(int dirfd, const char* path, int flags) {
    static int (*real___openat_2)(int, const char*, int) = nullptr;
    if (!real___openat_2) real___openat_2 = (decltype(real___openat_2))dlsym(RTLD_NEXT, "__openat_2");
    if (isHabPath(path)) return newHabFd();
    return real___openat_2(dirfd, path, flags);
}

extern "C" int open(const char* path, int flags, ...) {
    if (!real_open) real_open = (decltype(real_open))dlsym(RTLD_NEXT, "open");
    if (isHabPath(path)) return newHabFd();
    if (path && strcmp(path, "/dev/ion") == 0) return newIonFd();
    mode_t mode = 0;
    if (flags & (O_CREAT | O_TMPFILE)) { va_list ap; va_start(ap, flags); mode = va_arg(ap, mode_t); va_end(ap); }
    return real_open(path, flags, mode);
}

extern "C" int openat(int dirfd, const char* path, int flags, ...) {
    if (!real_openat) real_openat = (decltype(real_openat))dlsym(RTLD_NEXT, "openat");
    if (isHabPath(path)) return newHabFd();
    if (path && strcmp(path, "/dev/ion") == 0) return newIonFd();
    mode_t mode = 0;
    if (flags & (O_CREAT | O_TMPFILE)) { va_list ap; va_start(ap, flags); mode = va_arg(ap, mode_t); va_end(ap); }
    return real_openat(dirfd, path, flags, mode);
}

extern "C" int ioctl(int fd, int req, ...) {
    if (!real_ioctl) real_ioctl = (decltype(real_ioctl))dlsym(RTLD_NEXT, "ioctl");
    va_list ap; va_start(ap, req); void* arg = va_arg(ap, void*); va_end(ap);
    FdState* st = nullptr;
    bool ion = false;
    {
        std::lock_guard<std::mutex> g(gLock);
        auto it = gFds.find(fd);
        if (it != gFds.end()) st = it->second;
        ion = gIonFds.find(fd) != gIonFds.end();
    }
    if (ion || (unsigned long)(uint32_t)req == kIonIoctlAlloc) {
        if ((unsigned long)(uint32_t)req == kIonIoctlAlloc) {
            IonAllocReq* r = (IonAllocReq*)arg;
            int mfd = (int)syscall(__NR_memfd_create, "ion_emu", 1 /*MFD_CLOEXEC*/);
            if (mfd < 0) { errno = ENOMEM; return -1; }
            if (ftruncate(mfd, (off_t)r->len) != 0) { close(mfd); errno = ENOMEM; return -1; }
            LOG("ion alloc len=%llu -> fd %d", (unsigned long long)r->len, mfd);
            r->fd = mfd;
            return 0;
        }
        return 0;
    }
    if (st) return fakeIoctl(st, (unsigned long)(uint32_t)req, arg);
    return real_ioctl(fd, req, arg);
}

extern "C" int close(int fd) {
    if (!real_close) real_close = (decltype(real_close))dlsym(RTLD_NEXT, "close");
    FdState* st = nullptr;
    bool ion = false;
    {
        std::lock_guard<std::mutex> g(gLock);
        auto it = gFds.find(fd);
        if (it != gFds.end()) {
            st = it->second;
            gFds.erase(it);
        }
        auto iit = gIonFds.find(fd);
        if (iit != gIonFds.end()) { ion = true; gIonFds.erase(iit); }
    }
    if (ion) return real_close(fd);
    if (st) {
        int efd = st->efd;
        delete st;
        return real_close(efd);
    }
    return real_close(fd);
}

__attribute__((constructor)) static void shimInit() {
    real_open = (decltype(real_open))dlsym(RTLD_NEXT, "open");
    real_openat = (decltype(real_openat))dlsym(RTLD_NEXT, "openat");
    real_close = (decltype(real_close))dlsym(RTLD_NEXT, "close");
    real_ioctl = (decltype(real_ioctl))dlsym(RTLD_NEXT, "ioctl");
    LOG("loaded pid=%d", getpid());
}
