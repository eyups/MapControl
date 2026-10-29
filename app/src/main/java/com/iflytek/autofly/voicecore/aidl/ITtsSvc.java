package com.iflytek.autofly.voicecore.aidl;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;

/**
 * Minimal copy of the iFlytek VoiceCore TTS Binder interface.
 *
 * <p>The transaction numbers intentionally match the platform service. Using
 * this interface with stream type 3 (music) avoids the unit's broken
 * {@code tts-bus-speaker} route.</p>
 */
public interface ITtsSvc extends IInterface {

    int createTtsSession(VoiceServiceId id, int streamType,
                         ITtsStateListener listener, boolean innerSpeak);

    int setParam(VoiceServiceId id, int param, int value);

    int startSpeak(VoiceServiceId id, String text);

    int stopSpeak(VoiceServiceId id);

    int destroyTtsSession(VoiceServiceId id);

    abstract class Stub extends Binder implements ITtsSvc {
        private static final String DESCRIPTOR =
                "com.iflytek.autofly.voicecore.aidl.ITtsSvc";

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ITtsSvc asInterface(IBinder binder) {
            if (binder == null) {
                return null;
            }
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof ITtsSvc) {
                return (ITtsSvc) local;
            }
            return new Proxy(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            switch (code) {
                case 1: {
                    data.enforceInterface(DESCRIPTOR);
                    VoiceServiceId id = data.readInt() != 0
                            ? VoiceServiceId.CREATOR.createFromParcel(data) : null;
                    int result = createTtsSession(id, data.readInt(),
                            ITtsStateListener.Stub.asInterface(data.readStrongBinder()),
                            data.readInt() != 0);
                    reply.writeNoException();
                    reply.writeInt(result);
                    if (id != null) {
                        reply.writeInt(1);
                        id.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                    } else {
                        reply.writeInt(0);
                    }
                    return true;
                }
                case 2: {
                    data.enforceInterface(DESCRIPTOR);
                    VoiceServiceId id = data.readInt() != 0
                            ? VoiceServiceId.CREATOR.createFromParcel(data) : null;
                    int result = setParam(id, data.readInt(), data.readInt());
                    reply.writeNoException();
                    reply.writeInt(result);
                    return true;
                }
                case 3: {
                    data.enforceInterface(DESCRIPTOR);
                    VoiceServiceId id = data.readInt() != 0
                            ? VoiceServiceId.CREATOR.createFromParcel(data) : null;
                    int result = startSpeak(id, data.readString());
                    reply.writeNoException();
                    reply.writeInt(result);
                    return true;
                }
                case 6: {
                    data.enforceInterface(DESCRIPTOR);
                    VoiceServiceId id = data.readInt() != 0
                            ? VoiceServiceId.CREATOR.createFromParcel(data) : null;
                    int result = stopSpeak(id);
                    reply.writeNoException();
                    reply.writeInt(result);
                    return true;
                }
                case 8: {
                    data.enforceInterface(DESCRIPTOR);
                    VoiceServiceId id = data.readInt() != 0
                            ? VoiceServiceId.CREATOR.createFromParcel(data) : null;
                    int result = destroyTtsSession(id);
                    reply.writeNoException();
                    reply.writeInt(result);
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static final class Proxy implements ITtsSvc {
            private final IBinder remote;

            Proxy(IBinder remote) {
                this.remote = remote;
            }

            @Override
            public IBinder asBinder() {
                return remote;
            }

            private VoiceServiceId readId(Parcel data) {
                return data.readInt() != 0
                        ? VoiceServiceId.CREATOR.createFromParcel(data) : null;
            }

            private void writeId(Parcel data, VoiceServiceId id) {
                if (id != null) {
                    data.writeInt(1);
                    id.writeToParcel(data, 0);
                } else {
                    data.writeInt(0);
                }
            }

            private void transact(int code, Parcel data, Parcel reply) {
                try {
                    remote.transact(code, data, reply, 0);
                    reply.readException();
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public int createTtsSession(VoiceServiceId id, int streamType,
                                        ITtsStateListener listener, boolean innerSpeak) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    writeId(data, id);
                    data.writeInt(streamType);
                    data.writeStrongBinder(listener != null ? listener.asBinder() : null);
                    data.writeInt(innerSpeak ? 1 : 0);
                    transact(1, data, reply);
                    int result = reply.readInt();
                    if (id != null && reply.readInt() != 0) {
                        id.readFromParcel(reply);
                    }
                    return result;
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public int setParam(VoiceServiceId id, int param, int value) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    writeId(data, id);
                    data.writeInt(param);
                    data.writeInt(value);
                    transact(2, data, reply);
                    return reply.readInt();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public int startSpeak(VoiceServiceId id, String text) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    writeId(data, id);
                    data.writeString(text);
                    transact(3, data, reply);
                    return reply.readInt();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public int stopSpeak(VoiceServiceId id) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    writeId(data, id);
                    transact(6, data, reply);
                    return reply.readInt();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public int destroyTtsSession(VoiceServiceId id) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    writeId(data, id);
                    transact(8, data, reply);
                    return reply.readInt();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }
}
