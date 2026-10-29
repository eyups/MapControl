package com.iflytek.autofly.voicecore.aidl;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/** Minimal copy of the iFlytek VoiceCore TTS callback interface. */
public interface ITtsStateListener extends IInterface {

    void onTTSPlayBegin(boolean innerSpeak);

    void onTTSPlayCompleted(boolean innerSpeak);

    void onTTSPlayInterrupted(boolean innerSpeak);

    void onTTSProgressReturn(int progress, int duration);

    abstract class Stub extends Binder implements ITtsStateListener {
        private static final String DESCRIPTOR =
                "com.iflytek.autofly.voicecore.aidl.ITtsStateListener";

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ITtsStateListener asInterface(IBinder binder) {
            if (binder == null) {
                return null;
            }
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof ITtsStateListener) {
                return (ITtsStateListener) local;
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
                case 1:
                    data.enforceInterface(DESCRIPTOR);
                    onTTSPlayBegin(data.readInt() != 0);
                    reply.writeNoException();
                    return true;
                case 2:
                    data.enforceInterface(DESCRIPTOR);
                    onTTSPlayCompleted(data.readInt() != 0);
                    reply.writeNoException();
                    return true;
                case 3:
                    data.enforceInterface(DESCRIPTOR);
                    onTTSPlayInterrupted(data.readInt() != 0);
                    reply.writeNoException();
                    return true;
                case 4:
                    data.enforceInterface(DESCRIPTOR);
                    onTTSProgressReturn(data.readInt(), data.readInt());
                    reply.writeNoException();
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static final class Proxy implements ITtsStateListener {
            private final IBinder remote;

            Proxy(IBinder remote) {
                this.remote = remote;
            }

            @Override
            public IBinder asBinder() {
                return remote;
            }

            @Override
            public void onTTSPlayBegin(boolean innerSpeak) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(innerSpeak ? 1 : 0);
                    remote.transact(1, data, reply, 0);
                    reply.readException();
                } catch (RemoteException ignored) {
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void onTTSPlayCompleted(boolean innerSpeak) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(innerSpeak ? 1 : 0);
                    remote.transact(2, data, reply, 0);
                    reply.readException();
                } catch (RemoteException ignored) {
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void onTTSPlayInterrupted(boolean innerSpeak) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(innerSpeak ? 1 : 0);
                    remote.transact(3, data, reply, 0);
                    reply.readException();
                } catch (RemoteException ignored) {
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void onTTSProgressReturn(int progress, int duration) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(progress);
                    data.writeInt(duration);
                    remote.transact(4, data, reply, 0);
                    reply.readException();
                } catch (RemoteException ignored) {
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }
}
