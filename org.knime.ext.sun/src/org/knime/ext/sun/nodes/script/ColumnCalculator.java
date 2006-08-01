/* 
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2006
 * University of Konstanz, Germany.
 * Chair for Bioinformatics and Information Mining
 * Prof. Dr. Michael R. Berthold
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any quesions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * -------------------------------------------------------------------
 * 
 */
package org.knime.ext.sun.nodes.script;

import java.util.HashMap;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.IntValue;
import org.knime.core.data.RowKey;
import org.knime.core.data.StringValue;
import org.knime.core.data.container.CellFactory;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.NodeLogger;

import org.knime.ext.sun.nodes.script.expression.EvaluationFailedException;
import org.knime.ext.sun.nodes.script.expression.Expression;
import org.knime.ext.sun.nodes.script.expression.ExpressionInstance;
import org.knime.ext.sun.nodes.script.expression.IllegalPropertyException;

/**
 * Interface implementation that executes the java code snippet and calculates
 * the new column, either appended or replaced.
 * 
 * @author Bernd Wiswedel, University of Konstanz
 */
public class ColumnCalculator implements CellFactory {
    private static final NodeLogger LOGGER = NodeLogger
            .getLogger(ColumnCalculator.class);

    /**
     * Snippet code may contain the row number as parameter, it will be written
     * as "$$ROWNUMBER$$" (quotes excluded).
     */
    static final String ROWINDEX = "ROWNUMBER";

    /**
     * Snippet code may contain the row key as parameter, it will be written as
     * "$$ROWKEY$$" (quotes excluded).
     */
    static final String ROWKEY = "ROWKEY";

    private final ExpressionInstance m_expression;

    private final Class<?> m_returnType;

    private final DataTableSpec m_spec;

    private final DataColumnSpec[] m_colSpec;

    /**
     * The row index may be used for calculation. Need to be set immediately
     * before calculate is called.
     */
    private int m_lastProcessedRow = 0;

    /**
     * Creates new factory for a column appender. It creates an instance of the
     * temporary java code, sets the fields dynamically and evaluates the
     * expression.
     * 
     * @param expression the expression from which to create an instance
     * @param rType the return type, we need it to construct {@link DataCell}s
     * @param spec the table spec to read the field names and types
     * @param newColSpec the column spec for the newly generated column
     * @throws InstantiationException if the instance cannot be instantiated.
     */
    protected ColumnCalculator(final Expression expression, final Class rType,
            final DataTableSpec spec, final DataColumnSpec newColSpec)
            throws InstantiationException {
        if (expression == null) {
            throw new NullPointerException("Expression must not be null.");
        }
        if (spec == null) {
            throw new NullPointerException("Spec must not be null.");
        }
        if (!rType.equals(Double.class) && !rType.equals(Integer.class)
                && !rType.equals(String.class)) {
            throw new IllegalArgumentException("Invalid class: " + rType);
        }
        m_expression = expression.getInstance();
        m_returnType = rType;
        m_spec = spec;
        m_colSpec = new DataColumnSpec[]{newColSpec};
    }

    /**
     * @see org.knime.core.data.container.CellFactory#getColumnSpecs()
     */
    public DataColumnSpec[] getColumnSpecs() {
        return m_colSpec;
    }

    /**
     * @see CellFactory#getCells(DataRow)
     */
    public DataCell[] getCells(final DataRow row) {
        return new DataCell[]{calculate(row)};
    }

    /**
     * @see CellFactory#setProgress(int, int, RowKey, ExecutionMonitor)
     */
    public void setProgress(final int curRowNr, final int rowCount,
            final RowKey lastKey, final ExecutionMonitor exec) {
        m_lastProcessedRow = curRowNr;
        exec.setProgress(curRowNr / (double)rowCount, "Calculated row "
                + curRowNr + " (\"" + lastKey + "\")");
    }

    /**
     * Performs the calculation.
     * 
     * @param row the row to process
     * @return the resulting cell
     */
    public DataCell calculate(final DataRow row) {
        HashMap<String, Object> nameValueMap = new HashMap<String, Object>();
        nameValueMap.put(ROWINDEX, m_lastProcessedRow);
        nameValueMap.put(ROWKEY, row.getKey().getId().toString());
        for (int i = 0; i < row.getNumCells(); i++) {
            DataCell cell = row.getCell(i);
            DataType cellType = m_spec.getColumnSpec(i).getType();
            Object cellVal = null;
            if (!cell.isMissing()) {
                if (cellType.isCompatible(IntValue.class)) {
                    cellVal = new Integer(((IntValue)cell).getIntValue());
                } else if (cellType.isCompatible(DoubleValue.class)) {
                    cellVal = new Double(((DoubleValue)cell).getDoubleValue());
                } else if (cellType.isCompatible(StringValue.class)) {
                    cellVal = ((StringValue)cell).getStringValue();
                } else {
                    cellVal = cell.toString();
                }
            }
            if (cellVal != null) {
                String colFieldName = createColField(i);
                nameValueMap.put(colFieldName, cellVal);
            }
        }
        Object o = null;
        try {
            m_expression.set(nameValueMap);
            o = m_expression.evaluate();
            if (!(m_returnType.isAssignableFrom(o.getClass()))) {
                LOGGER.warn("Unable to cast return type of instantj "
                        + "expression \"" + o.getClass().getName()
                        + "\" to desired output \"" + m_returnType.getName()
                        + "\" - putting missing value instead.");
                o = null;
            }
        } catch (EvaluationFailedException ee) {
            LOGGER.warn("Evaluation of expression failed for row \""
                    + row.getKey().getId() + "\"", ee);
        } catch (IllegalPropertyException ipe) {
            LOGGER.warn("Evaluation of expression failed for row \""
                    + row.getKey().getId() + "\"", ipe);
        }
        DataCell result;
        if (m_returnType.equals(Integer.class)) {
            if (o == null) {
                result = DataType.getMissingCell();
            } else {
                result = new IntCell(((Integer)o).intValue());
            }
        } else if (m_returnType.equals(Double.class)) {
            if (o == null || ((Double)o).isNaN()) {
                result = DataType.getMissingCell();
            } else {
                result = new DoubleCell(((Double)o).doubleValue());
            }
        } else if (m_returnType.equals(String.class)) {
            if (o == null) {
                result = DataType.getMissingCell();
            } else {
                result = new StringCell((String)o);
            }
        } else {
            throw new InternalError();
        }
        return result;
    }

    /**
     * Get name of the field as it is used in the temp-java file.
     * 
     * @param col the number of the column
     * @return "col" + col
     */
    static String createColField(final int col) {
        return "col" + col;
    }
}
