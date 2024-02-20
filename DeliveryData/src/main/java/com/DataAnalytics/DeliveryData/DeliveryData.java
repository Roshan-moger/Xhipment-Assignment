package com.DataAnalytics.DeliveryData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class DeliveryData {
    static class ShipmentData {
        private String kind;
        private String milestonesJson;

        public ShipmentData(String kind, String milestonesJson) {
            this.kind = kind;
            this.milestonesJson = milestonesJson;
        }

        public String getKind() {
            return kind;
        }

        public String getMilestonesJson() {
            return milestonesJson;
        }
    }

    private static List<ShipmentData> readDataFromExcel(String filePath) throws IOException, ParseException {
        List<ShipmentData> shipmentDataList = new ArrayList<>();

        try (FileInputStream fileInputStream = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fileInputStream)) {

            Sheet sheet = workbook.getSheetAt(0); // Assuming data is in the first sheet

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);

                String kind = row.getCell(8).getStringCellValue(); // Assuming "Kind" is in the 9th column (index 8)
                String milestonesJson = row.getCell(4).getStringCellValue(); // Assuming milestones is in the 5th column (index 4)

                shipmentDataList.add(new ShipmentData(kind, milestonesJson));
            }
        }

        return shipmentDataList;
    }

    public static void main(String[] args) {
        try {
            // Replace with the actual path to your Excel file
            String excelFilePath = "C:\\Users\\user\\Downloads\\Data_Sheet.xlsx";

            // Read data from Excel
            List<ShipmentData> shipmentDataList = readDataFromExcel(excelFilePath);

            // Filter and collect data where 'kind' is not null
            List<ShipmentData> filteredData = shipmentDataList.stream()
                    .filter(data -> data.getKind() != null)
                    .collect(Collectors.toList());

            // Calculate average delivery time
            Map<String, Map<String, Double>> averageDeliveryTimeMap = calculateAverageDeliveryTime(filteredData);

            // Print average delivery time
            System.out.println("Average Delivery Time for each product type (fcl, lcl, etc) for each month in 2023:");
            printDeliveryTimeMap(averageDeliveryTimeMap);

            // Calculate tp90 delivery time
            Map<String, Map<String, Double>> tp90DeliveryTimeMap = calculateTp90DeliveryTime(filteredData);

            // Print tp90 delivery time
            System.out.println("\ntp90 Delivery Time for each product type (fcl, lcl, etc) for each month in 2023:");
            printDeliveryTimeMap(tp90DeliveryTimeMap);

        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    private static void printDeliveryTimeMap(Map<String, Map<String, Double>> deliveryTimeMap) {
        for (Map.Entry<String, Map<String, Double>> monthEntry : deliveryTimeMap.entrySet()) {
            String month = monthEntry.getKey();
            Map<String, Double> productTypeMap = monthEntry.getValue();

            for (Map.Entry<String, Double> entry : productTypeMap.entrySet()) {
                String productType = entry.getKey();
                double deliveryTime = entry.getValue();

                System.out.printf("Month: %s, Product Type: %s, Delivery Time: %.2f days%n", month, productType, deliveryTime);
            }
        }
    }

    private static Map<String, Map<String, Double>> calculateAverageDeliveryTime(List<ShipmentData> data) {
        Map<String, Map<String, List<Double>>> productTypeDeliveryTimes = new HashMap<>();

        for (ShipmentData shipmentData : data) {
            String month = getMonthFromData(shipmentData);
            String productType = getProductTypeFromData(shipmentData);
            double deliveryTime = calculateDeliveryTime(shipmentData.getMilestonesJson());

            if (month != null && productType != null) {
                productTypeDeliveryTimes.computeIfAbsent(month, k -> new HashMap<>())
                        .computeIfAbsent(productType, k -> new ArrayList<>())
                        .add(deliveryTime);
            }
        }

        Map<String, Map<String, Double>> result = new HashMap<>();

        for (Map.Entry<String, Map<String, List<Double>>> monthEntry : productTypeDeliveryTimes.entrySet()) {
            String month = monthEntry.getKey();
            Map<String, List<Double>> productTypeMap = monthEntry.getValue();

            Map<String, Double> averageMap = productTypeMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> calculateAverage(entry.getValue())
                    ));

            result.put(month, averageMap);
        }

        return result;
    }

    private static Map<String, Map<String, Double>> calculateTp90DeliveryTime(List<ShipmentData> data) {
        Map<String, Map<String, List<Double>>> productTypeDeliveryTimes = new HashMap<>();

        for (ShipmentData shipmentData : data) {
            String month = getMonthFromData(shipmentData);
            String productType = getProductTypeFromData(shipmentData);
            double deliveryTime = calculateDeliveryTime(shipmentData.getMilestonesJson());

            if (month != null && productType != null) {
                productTypeDeliveryTimes.computeIfAbsent(month, k -> new HashMap<>())
                        .computeIfAbsent(productType, k -> new ArrayList<>())
                        .add(deliveryTime);
            }
        }

        Map<String, Map<String, Double>> result = new HashMap<>();

        for (Map.Entry<String, Map<String, List<Double>>> monthEntry : productTypeDeliveryTimes.entrySet()) {
            String month = monthEntry.getKey();
            Map<String, List<Double>> productTypeMap = monthEntry.getValue();

            Map<String, Double> tp90Map = productTypeMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> calculateTp90(entry.getValue())
                    ));

            result.put(month, tp90Map);
        }

        return result;
    }

    private static double calculateAverage(List<Double> values) {
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private static double calculateTp90(List<Double> deliveryTimes) {
        Collections.sort(deliveryTimes);
        int index = (int) Math.ceil(0.9 * deliveryTimes.size()) - 1;
        return deliveryTimes.get(index);
    }

    private static String getMonthFromData(ShipmentData shipmentData) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            JsonNode milestonesArray = objectMapper.readTree(shipmentData.getMilestonesJson());

            for (JsonNode milestone : milestonesArray) {
                JsonNode dateTimeNode = milestone.get("dateTime");
                if (dateTimeNode != null && !dateTimeNode.isNull() && dateTimeNode.isTextual()) {
                    String deliveredDateStr = dateTimeNode.asText();
                    if (!"null".equals(deliveredDateStr)) {
                        ZonedDateTime deliveredDate = ZonedDateTime.parse(deliveredDateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        return deliveredDate.format(DateTimeFormatter.ofPattern("MMM''yy"));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Return null if month is not found or cannot be parsed
        return null;
    }


    private static String getProductTypeFromData(ShipmentData shipmentData) {
        // Implement your logic to get the product type
        String productType = shipmentData.getKind();
        return productType != null ? productType : "NULL";
    }

    private static double calculateDeliveryTime(String milestonesJson) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            JsonNode milestonesArray = objectMapper.readTree(milestonesJson);

            ZonedDateTime bookingConfirmedDate = null;
            ZonedDateTime deliveredDate = null;

            for (JsonNode milestone : milestonesArray) {
                JsonNode dateTimeNode = milestone.get("dateTime");
                if (dateTimeNode != null && !dateTimeNode.isNull() && !"null".equals(dateTimeNode.asText())) {
                    if ("Booking Confirmed".equals(milestone.get("value").asText())) {
                        bookingConfirmedDate = ZonedDateTime.parse(dateTimeNode.asText(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    } else if ("Delivered to Consignee".equals(milestone.get("value").asText())) {
                        deliveredDate = ZonedDateTime.parse(dateTimeNode.asText(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    }
                }
            }

            if (bookingConfirmedDate != null && deliveredDate != null) {
                Duration duration = Duration.between(bookingConfirmedDate, deliveredDate);
                return duration.toDays();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 0; // Return a default value if booking confirmation date is not found
    }
}
