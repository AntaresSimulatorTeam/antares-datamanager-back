package com.rte_france.antares.datamanager_back.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileInputStream;

/**
 * Example client for calling the Trajectory Settings Import API
 * 
 * This component demonstrates how to import trajectory settings from an Excel file
 * using the TrajectorySettingsController API.
 */
public class TrajectorySettingsImportExampleClient {

    private static final String API_BASE_URL = "http://localhost:8080/v1/trajectory/settings";

    private final RestTemplate restTemplate;

    public TrajectorySettingsImportExampleClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Import trajectory settings from an Excel file
     * 
     * @param filePath Path to the Excel file (e.g., "src/main/resources/general_data_BP23_A_ref_200MC.xlsx")
     * @param trajectoryId ID of the trajectory to import settings into
     * @return Response message
     */
    public String importTrajectorySettings(String filePath, Integer trajectoryId) {
        try {
            File file = new File(filePath);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new org.springframework.core.io.FileSystemResource(file));
            
            String url = API_BASE_URL + "/import?trajectoryId=" + trajectoryId;
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    body,
                    String.class
            );
            
            System.out.println("Import completed with status: " + response.getStatusCode());
            System.out.println("Response: " + response.getBody());
            
            return response.getBody();
        } catch (Exception e) {
            System.err.println("Error importing trajectory settings: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Example usage
     */
    public static void main(String[] args) {
        System.out.println("Example usage:");
        System.out.println("TrajectorySettingsImportExampleClient client = new TrajectorySettingsImportExampleClient(restTemplate);");
        System.out.println("client.importTrajectorySettings(");
        System.out.println("  \"src/main/resources/general_data_BP23_A_ref_200MC.xlsx\",");
        System.out.println("  1  // trajectoryId");
        System.out.println(");");
    }
}
