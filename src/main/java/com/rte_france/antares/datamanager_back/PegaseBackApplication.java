package com.rte_france.antares.datamanager_back;

import com.rte_france.antares.datamanager_back.configuration.CorsConfig;
import com.rte_france.antares.datamanager_back.configuration.OpenApiConfig;
import com.rte_france.antares.datamanager_back.configuration.SecurityConfig;
import com.rte_france.antares.datamanager_back.service.TimeSeriesStorageService;
import com.rte_france.antares.timeseries_manager.structures.TimeSeriesMatrix;
import com.rte_france.antares.timeseries_manager.structures.TimeSeriesMatrixColumn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;
import java.util.List;

@Import({SecurityConfig.class, OpenApiConfig.class, CorsConfig.class})
@SpringBootApplication
public class PegaseBackApplication implements CommandLineRunner {
	@Autowired
	private TimeSeriesStorageService timeSeriesStorageService;

	public static void main(String[] args) {
		SpringApplication.run(PegaseBackApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		double[] values = {1.0, 2.0, 3.0, 4.0};
		var column = new TimeSeriesMatrixColumn("SampleColumn", values);
		var matrix = new TimeSeriesMatrix(List.of(column));

		var outputPath = Path.of("output.arrow");
		var inputPath = outputPath;

		timeSeriesStorageService.writeTimeSeries(matrix, outputPath);
		System.out.println("TimeSeriesMatrix written to file: " + outputPath);

		var readMatrix = timeSeriesStorageService.readTimeSeries(inputPath);
		System.out.println("TimeSeriesMatrix read from file: " + readMatrix);
	}
}

