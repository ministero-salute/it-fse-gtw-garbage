package it.finanze.sanita.fse2.ms.gtw.garbage.service;

public interface IConfigSRV {
    Integer getValidatedDocRetentionDay();
    Integer getConfigItemsRetentionDay();
    long getRefreshRate();
}
