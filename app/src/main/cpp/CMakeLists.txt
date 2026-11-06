cmake_minimum_required(VERSION 3.22.1)
project(roaddeck_hab)

add_library(roaddeck_hab SHARED hab_native.cpp)

# Kare donusumu/kopyasi debug derlemede de akici kalsin.
# Gorunurluk gizli: yalnizca JNIEXPORT (Java_*) fonksiyonlari disa acik kalir.
# Kullanilmayan fonksiyon/veri bolumleri baglayicida atilir (kutuphane boyutu).
target_compile_options(roaddeck_hab PRIVATE
        -O2
        -fvisibility=hidden
        -fvisibility-inlines-hidden
        -ffunction-sections
        -fdata-sections)
# --exclude-libs: statik baglanan libc++ sembolleri de disa acilmasin.
target_link_options(roaddeck_hab PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)

find_library(log-lib log)
target_link_libraries(roaddeck_hab ${log-lib} EGL GLESv2)
