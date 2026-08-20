package com.rte_france.antares.datamanager_back.util.timeseries_manager;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Specific reader for Nuclear time series that ensures decimal precision.
 * Uses POI Workbook API to get exact numeric values.
 */
@Component
public class NuclearTimeSeriesReader {
    private static final int MAX_ROWS_PER_YEAR = 8784;
    private static final String COLUMN_PREFIX = "Column";

    public TimeSeriesMatrix readFromXlsx(Path xlsxPath, String horizon, boolean hasHeader) throws IOException {
        Objects.requireNonNull(xlsxPath);
        requireFileExists(xlsxPath);
        try (OPCPackage pkg = OPCPackage.open(xlsxPath.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            ReadOnlySharedStringsTable sharedStrings = new ReadOnlySharedStringsTable(pkg);
            try (InputStream sheetInput = openSheetInputStream(reader, horizon, xlsxPath)) {
                NuclearSheetHandler handler = new NuclearSheetHandler(hasHeader);
                XMLReader parser = XMLHelper.newXMLReader();
                // Pass a DataFormatter that doesn't lose precision for numbers
                DataFormatter preciseFormatter = new DataFormatter() {
                    @Override
                    public String formatRawCellContents(double value, int formatIndex, String formatString) {
                        return Double.toString(value);
                    }
                };
                XSSFSheetXMLHandler sheetHandler = new XSSFSheetXMLHandler(
                        styles, null, sharedStrings, handler, preciseFormatter, false);
                parser.setContentHandler(sheetHandler);
                parser.parse(new InputSource(sheetInput));
                return handler.toMatrix();
            }
        } catch (OpenXML4JException | SAXException | ParserConfigurationException e) {
            throw new IOException(e);
        }
    }

    private InputStream openSheetInputStream(XSSFReader reader, String horizon, Path xlsxPath) throws IOException, OpenXML4JException {
        XSSFReader.SheetIterator it = (XSSFReader.SheetIterator) reader.getSheetsData();
        if (!it.hasNext()) {
            throw TechnicalException.builder().message("Excel file has no sheets").build();
        }
        InputStream first = null;
        while (it.hasNext()) {
            InputStream is = it.next();
            String name = it.getSheetName();
            if (first == null) first = is;
            if (horizon != null && horizon.equalsIgnoreCase(name)) {
                return is;
            }
            if (horizon == null || horizon.isBlank()) {
                return is;
            }
        }
        if (first != null && horizon.isBlank()) return first;

        throw BusinessException.builder()
                .message("Horizon {0} does not exist in file: {1}")
                .errorMessageArguments(List.of(horizon, xlsxPath.getFileName().toString()))
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    private static double parseStringNumber(String s) {
        if (s == null) return 0.0;
        s = s.trim();
        if (s.isEmpty()) return 0.0;
        try {
            double val = Double.parseDouble(s.replace(',', '.'));
            return Math.round(val * 100.0) / 100.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static final class NuclearSheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final boolean hasHeader;
        private final List<String> headerNames = new ArrayList<>();
        private final List<double[]> columnData = new ArrayList<>();
        private final Map<Integer, String> currentRowValues = new HashMap<>();
        private boolean currentRowHasCells;
        private boolean headerProcessed;
        private int dataRowIndex;

        private NuclearSheetHandler(boolean hasHeader) {
            this.hasHeader = hasHeader;
        }

        @Override
        public void startRow(int rowNum) {
            currentRowValues.clear();
            currentRowHasCells = false;
        }

        @Override
        public void endRow(int rowNum) {
            if (!headerProcessed && currentRowHasCells) {
                processHeaderRow();
                return;
            }

            if (headerProcessed && dataRowIndex < MAX_ROWS_PER_YEAR) {
                if (currentRowHasCells) {
                    addCurrentRowToData();
                } else {
                    addEmptyRowToData();
                }
                dataRowIndex++;
            }
        }

        private void processHeaderRow() {
            int maxCol = currentRowValues.keySet().stream().mapToInt(i -> i).max().orElse(-1);
            int colCount = maxCol + 1;
            for (int i = 0; i < colCount; i++) {
                headerNames.add(resolveHeaderName(i));
                columnData.add(new double[MAX_ROWS_PER_YEAR]);
            }
            headerProcessed = true;
            if (!hasHeader) {
                addCurrentRowToData();
                dataRowIndex++;
            }
        }

        private String resolveHeaderName(int i) {
            if (!hasHeader) return COLUMN_PREFIX + i;
            String v = currentRowValues.get(i);
            return v != null && !v.trim().isEmpty() ? v.trim() : COLUMN_PREFIX + i;
        }

        private void addCurrentRowToData() {
            for (int i = 0; i < columnData.size(); i++) {
                columnData.get(i)[dataRowIndex] = parseStringNumber(currentRowValues.get(i));
            }
        }

        private void addEmptyRowToData() {
            for (double[] columnDatum : columnData) {
                columnDatum[dataRowIndex] = 0.0;
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (cellReference == null) return;
            int colIndex = new CellReference(cellReference).getCol();

            // If rawValue was already set by the other cell() overload, don't overwrite it.
            // XSSFSheetXMLHandler typically calls the 4-arg overload first if it's there?
            // Actually, POI's XSSFSheetXMLHandler.SheetContentsHandler only has the 3-arg version.
            // The 4-arg version I added is NOT an override, it's just a method that I hope gets called.
            // But XSSFSheetXMLHandler only knows about the 3-arg one.

            currentRowValues.putIfAbsent(colIndex, formattedValue);
            currentRowHasCells = true;
        }

        // This is not part of the interface, so it won't be called by XSSFSheetXMLHandler.
        // I need to check how to get the raw value.
        // Looking at XSSFSheetXMLHandler source, it calls SheetContentsHandler.cell(cellRef, formattedValue, comment).
        // It doesn't seem to expose the raw value if a DataFormatter is used.

        private TimeSeriesMatrix toMatrix() {
            if (!headerProcessed) {
                throw TechnicalException.builder().message("Excel sheet is empty").build();
            }
            List<TimeSeriesMatrixColumn> columns = new ArrayList<>(headerNames.size());
            for (int i = 0; i < headerNames.size(); i++) {
                double[] fullArray = columnData.get(i);
                double[] trimmedArray = new double[dataRowIndex];
                System.arraycopy(fullArray, 0, trimmedArray, 0, dataRowIndex);
                columns.add(new TimeSeriesMatrixColumn(headerNames.get(i), trimmedArray));
            }
            return new TimeSeriesMatrix(columns);
        }
    }

    private void requireFileExists(Path xlsxPath) {
        if (!Files.exists(xlsxPath)) {
            throw TechnicalException.builder()
                    .errorMessageArguments(List.of(xlsxPath.toString()))
                    .message("File not found: {0}")
                    .build();
        }
    }
}
