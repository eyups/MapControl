package com.iflytek.autofly.voicecore.aidl;

import android.os.Parcel;
import android.os.Parcelable;

/** Minimal copy of the iFlytek VoiceCore session id parcelable. */
public class VoiceServiceId implements Parcelable {
    public static final Creator<VoiceServiceId> CREATOR = new Creator<VoiceServiceId>() {
        @Override
        public VoiceServiceId createFromParcel(Parcel parcel) {
            return new VoiceServiceId(parcel);
        }

        @Override
        public VoiceServiceId[] newArray(int size) {
            return new VoiceServiceId[size];
        }
    };

    private String id;

    public VoiceServiceId() {
    }

    public VoiceServiceId(String id) {
        this.id = id;
    }

    private VoiceServiceId(Parcel parcel) {
        readFromParcel(parcel);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void readFromParcel(Parcel parcel) {
        id = parcel.readString();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(id);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "VoiceServiceId{id='" + id + "'}";
    }
}
