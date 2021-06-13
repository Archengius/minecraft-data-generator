package me.arch.mcdatagen.generators;

import com.google.gson.JsonElement;

public interface IDataGenerator {

    String getDataName();

    JsonElement generateDataJson();
}
