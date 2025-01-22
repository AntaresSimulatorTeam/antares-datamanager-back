/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.datamanager_back.util;

import lombok.Value;

import java.util.List;

/**
 * @author Sylvain Leclerc <sylvain.leclerc@rte-france.com>
 */
@Value
public class Matrix {

    List<MatrixColumn> columns;

    public int getRowCount() {
        if (columns.isEmpty()) {
            return 0;
        }
        return columns.getFirst().getSize();
    }

}
