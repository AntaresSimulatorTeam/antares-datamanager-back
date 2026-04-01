package com.rte_france.antares.datamanager_back.service.sts;

import com.rte_france.antares.datamanager_back.repository.model.StConstraintsParameterEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface StStorageConstraintsFileProcessorService {


    List<StConstraintsParameterEntity> processConstraintsParametersAnHoursFile (Path additionalConstraintsPath) throws IOException;
}
