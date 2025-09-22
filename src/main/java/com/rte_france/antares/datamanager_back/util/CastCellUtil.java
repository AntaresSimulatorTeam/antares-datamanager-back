package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class CastCellUtil {


    public static Integer castInt(Object cellValue) {
        if (cellValue == null) return null;
        else if (cellValue instanceof Number n) {
            return n.intValue();
        }
        return null; // couvrir les autres types
    }

    public static String castString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

   public static Double castDouble(Object o, String columnName) {
        if (o == null) return null;
        try {
            java.math.BigDecimal bd;
            if (o instanceof Number n) {
                bd = java.math.BigDecimal.valueOf(n.doubleValue());
            } else {
                bd = new java.math.BigDecimal(String.valueOf(o));
            }
            bd = bd.setScale(2, java.math.RoundingMode.HALF_UP);
            return bd.doubleValue();
        } catch (NumberFormatException e) {
            throw BusinessException.builder()
                    .message("The value '" + o + "' in column '" + columnName + "' is not numeric")
                    .build();
        }
    }
}
