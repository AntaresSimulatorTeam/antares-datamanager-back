package com.rte_france.antares.datamanager_back.service;


import java.io.IOException;
import java.nio.file.Path;

public interface LoadFileProcessorService {
  String saveMatrixToNas(Path path) throws IOException;
}
