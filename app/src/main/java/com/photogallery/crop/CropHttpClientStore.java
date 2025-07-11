package com.photogallery.crop;

import androidx.annotation.NonNull;

import okhttp3.OkHttpClient;

public class CropHttpClientStore {
    private CropHttpClientStore() {
    }

    public final static CropHttpClientStore INSTANCE = new CropHttpClientStore();
    private OkHttpClient client;

    @NonNull
    public OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient();
        }
        return client;
    }
}
