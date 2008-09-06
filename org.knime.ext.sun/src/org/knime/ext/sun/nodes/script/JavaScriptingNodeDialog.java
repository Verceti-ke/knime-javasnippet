/* 
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2008
 * University of Konstanz, Germany
 * Chair for Bioinformatics and Information Mining (Prof. M. Berthold)
 * and KNIME GmbH, Konstanz, Germany
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * -------------------------------------------------------------------
 * 
 */
package org.knime.ext.sun.nodes.script;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;

import org.knime.base.util.scopevariable.ScopeVariableListCellRenderer;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.util.ColumnSelectionPanel;
import org.knime.core.node.util.DataColumnSpecListCellRenderer;
import org.knime.core.node.workflow.ScopeVariable;
import org.knime.ext.sun.nodes.script.expression.CompilationFailedException;

/**
 * 
 * @author Bernd Wiswedel, University of Konstanz
 */
public class JavaScriptingNodeDialog extends NodeDialogPane {

    private final JList m_colList;
    
    private final JList m_scopeVarsList;

    private final JEditorPane m_expEdit;

    private final JRadioButton m_appendRadio;

    private final JTextField m_newColNameField;

    private final JRadioButton m_replaceRadio;

    private final ColumnSelectionPanel m_replaceCombo;

    private final ButtonGroup m_returnTypeButtonGroup;
    
    private final JCheckBox m_compileOnCloseChecker;

    private DataTableSpec m_currenteSpec = null;

    /** Inits GUI. */
    @SuppressWarnings("unchecked")
    public JavaScriptingNodeDialog() {
        m_colList = new JList(new DefaultListModel());
        m_colList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        m_colList.addKeyListener(new KeyAdapter() {
            /** {@inheritDoc} */
            @Override
            public void keyTyped(final KeyEvent e) {
                if (e.getKeyChar() == KeyEvent.VK_ENTER) {
                    Object selected = m_colList.getSelectedValue();
                    if (selected != null) {
                        onSelectionInColumnList(selected);
                    }
                }
            }
        });
        m_colList.addMouseListener(new MouseAdapter() {
            /** {@inheritDoc} */
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Object selected = m_colList.getSelectedValue();
                    if (selected != null) {
                        onSelectionInColumnList(selected);
                    }
                }
            }
        });
        m_colList.setCellRenderer(new ListRenderer());
        m_scopeVarsList = new JList(new DefaultListModel());
        m_scopeVarsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        m_scopeVarsList.setToolTipText(""); // enable tooltip
        m_scopeVarsList.addKeyListener(new KeyAdapter() {
            /** {@inheritDoc} */
            @Override
            public void keyTyped(final KeyEvent e) {
                if (e.getKeyChar() == KeyEvent.VK_ENTER) {
                    Object selected = m_scopeVarsList.getSelectedValue();
                    if (selected != null) {
                        onSelectionInVariableList(selected);
                    }
                }
            }
        });
        m_scopeVarsList.addMouseListener(new MouseAdapter() {
            /** {@inheritDoc} */
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Object selected = m_scopeVarsList.getSelectedValue();
                    if (selected != null) {
                        onSelectionInVariableList(selected);
                    }
                }
            }
        });
        m_scopeVarsList.setCellRenderer(new ScopeVariableListCellRenderer());
        m_expEdit = new JEditorPane();
        Font font = m_expEdit.getFont();
        m_expEdit.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 
                (font == null ? 12 : font.getSize())));
        m_newColNameField = new JTextField(10);
        m_appendRadio = new JRadioButton("Append Column: ");
        m_appendRadio.setToolTipText("Appends a new column to the input "
                + "table with a given name and type.");
        m_replaceRadio = new JRadioButton("Replace Column: ");
        m_replaceRadio.setToolTipText("Replaces the column and changes "
                + "the column type accordingly");
        // show all columns
        m_replaceCombo = 
            new ColumnSelectionPanel((Border)null, DataValue.class);
        m_replaceCombo.setRequired(false);
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(m_appendRadio);
        buttonGroup.add(m_replaceRadio);
        ActionListener actionListener = new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                m_replaceCombo.setEnabled(m_replaceRadio.isSelected());
                m_newColNameField.setEnabled(m_appendRadio.isSelected());
            }
        };
        m_appendRadio.addActionListener(actionListener);
        m_replaceRadio.addActionListener(actionListener);
        
        m_compileOnCloseChecker = new JCheckBox("Compile on close");
        m_compileOnCloseChecker.setToolTipText("Compiles the code on close "
                + "to identify potential syntax problems.");

        JRadioButton intReturnRadio = new JRadioButton("Integer");
        intReturnRadio.setActionCommand(Integer.class.getName());
        JRadioButton doubleReturnRadio = new JRadioButton("Double");
        doubleReturnRadio.setActionCommand(Double.class.getName());
        JRadioButton stringReturnRadio = new JRadioButton("String");
        stringReturnRadio.setActionCommand(String.class.getName());
        m_returnTypeButtonGroup = new ButtonGroup();
        m_returnTypeButtonGroup.add(intReturnRadio);
        m_returnTypeButtonGroup.add(doubleReturnRadio);
        m_returnTypeButtonGroup.add(stringReturnRadio);

        addTab("Java Scripting", createPanel());
    }
    
    private void onSelectionInColumnList(final Object selected) {
        if (selected != null) {
            String enter;
            if (selected instanceof String) {
                enter = "$$" + selected + "$$";
            } else {
                DataColumnSpec colSpec = (DataColumnSpec)selected;
                String name = colSpec.getName().replace("$", "\\$");
                enter = "$" + name + "$";
            }
            m_expEdit.replaceSelection(enter);
            m_colList.clearSelection();
            m_expEdit.requestFocus();
        }
    }
    
    private void onSelectionInVariableList(final Object selected) {
        if (selected instanceof ScopeVariable) {
            ScopeVariable v = (ScopeVariable)selected;
            String typeChar;
            switch (v.getType()) {
            case DOUBLE:
                typeChar = "D";
                break;
            case INTEGER:
                typeChar = "I";
                break;
            case STRING:
                typeChar = "S";
                break;
            default:
                return;
            }
            String enter = "$${" + typeChar + v.getName() + "}$$";
            m_expEdit.replaceSelection(enter);
            m_scopeVarsList.clearSelection();
            m_expEdit.requestFocus();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings,
            final DataTableSpec[] specs) throws NotConfigurableException {
        JavaScriptingSettings s = new JavaScriptingSettings();
        s.loadSettingsInDialog(settings, specs[0]);
        String exp = s.getExpression();
        String rType = s.getReturnType().getName();
        String defaultColName = "new column";
        String newColName = s.getColName();
        boolean isReplace = s.isReplace();
        boolean isTestCompilation = s.isTestCompilationOnDialogClose();
        m_newColNameField.setText("");
        // will select newColName only if it is in the spec list
        m_replaceCombo.update(specs[0], newColName);
        m_currenteSpec = specs[0];
        if (isReplace && m_replaceCombo.getNrItemsInList() > 0) {
            m_replaceRadio.doClick();
        } else {
            m_appendRadio.doClick();
            String newColString = (newColName != null ? newColName
                    : defaultColName);
            m_newColNameField.setText(newColString);
        }
        m_replaceRadio.setEnabled(m_replaceCombo.getNrItemsInList() > 0);
        m_expEdit.setText(exp);
        ButtonModel firstButton = null;
        for (Enumeration<?> e = m_returnTypeButtonGroup.getElements(); e
                .hasMoreElements();) {
            AbstractButton b = (AbstractButton)e.nextElement();
            if (firstButton == null) {
                firstButton = b.getModel();
            }
            if (b.getActionCommand().equals(rType)) {
                m_returnTypeButtonGroup.setSelected(b.getModel(), true);
            }
        }
        if (m_returnTypeButtonGroup.getSelection() == null) {
            m_returnTypeButtonGroup.setSelected(firstButton, true);
        }
        DefaultListModel listModel = (DefaultListModel)m_colList.getModel();
        listModel.removeAllElements();
        listModel.addElement(ColumnCalculator.ROWKEY);
        listModel.addElement(ColumnCalculator.ROWINDEX);
        for (int i = 0; i < specs[0].getNumColumns(); i++) {
            DataColumnSpec colSpec = specs[0].getColumnSpec(i);
            listModel.addElement(colSpec);
        }
        DefaultListModel svListModel = 
            (DefaultListModel)m_scopeVarsList.getModel();
        svListModel.removeAllElements();
        for (ScopeVariable v : getAvailableScopeVariables().values()) {
            svListModel.addElement(v);
        }
        m_compileOnCloseChecker.setSelected(isTestCompilation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings)
            throws InvalidSettingsException {
        JavaScriptingSettings s = new JavaScriptingSettings();
        String newColName = null;
        boolean isReplace = m_replaceRadio.isSelected();
        if (isReplace) {
            newColName = m_replaceCombo.getSelectedColumn();
        } else {
            newColName = m_newColNameField.getText();
        }
        s.setReplace(isReplace);
        s.setColName(newColName);
        String type = m_returnTypeButtonGroup.getSelection().getActionCommand();
        s.setReturnType(type);
        String exp = m_expEdit.getText();
        s.setExpression(exp);
        boolean isTestCompilation = m_compileOnCloseChecker.isSelected();
        s.setTestCompilationOnDialogClose(isTestCompilation);
        if (isTestCompilation && m_currenteSpec != null) {
            File tempFile = null;
            File classFile = null;
            try {
                tempFile = JavaScriptingNodeModel.createTempFile();
                classFile = 
                    JavaScriptingNodeModel.getAccompanyingClassFile(tempFile);
                Class<?> rType = s.getReturnType();
                JavaScriptingNodeModel.compile(exp, m_currenteSpec, rType,
                        tempFile);
            } catch (CompilationFailedException cfe) {
                throw new InvalidSettingsException(cfe.getMessage());
            } catch (IOException ioe) {
                // do nothing, leave it up to the caller to validate the
                // settings
            } finally {
                if (tempFile != null) {
                    tempFile.delete();
                }
                if (classFile != null) {
                    classFile.delete();
                }
            }
        }
        s.saveSettingsTo(settings);
    }

    private JPanel createPanel() {
        JPanel finalPanel = new JPanel(new BorderLayout());
        final JSplitPane varSplitPane = 
            new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JScrollPane pane = new JScrollPane(m_colList);
        pane.setBorder(BorderFactory.createTitledBorder(" Column List "));
        varSplitPane.setTopComponent(pane);
        pane = new JScrollPane(m_scopeVarsList);
        pane.setBorder(BorderFactory.createTitledBorder(
                " Flow Variable List "));
        varSplitPane.setBottomComponent(pane);
        varSplitPane.setOneTouchExpandable(true);
        varSplitPane.setResizeWeight(0.9);

        JPanel centerPanel = new JPanel(new GridLayout(0, 1));
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setLeftComponent(varSplitPane);
        mainSplitPane.setRightComponent(new JScrollPane(m_expEdit));
        centerPanel.add(mainSplitPane);

        JPanel southPanel = new JPanel(new GridLayout(0, 2));
        JPanel replaceOrAppend = new JPanel(new GridLayout(0, 2));
        replaceOrAppend.setBorder(BorderFactory
                .createTitledBorder("Replace or append result"));
        replaceOrAppend.add(m_appendRadio);
        replaceOrAppend.add(m_newColNameField);
        replaceOrAppend.add(m_replaceRadio);
        replaceOrAppend.add(m_replaceCombo);
        southPanel.add(replaceOrAppend);

        JPanel returnTypeAndCompilation = new JPanel(new BorderLayout());
        JPanel compilationPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        compilationPanel.add(m_compileOnCloseChecker);
        returnTypeAndCompilation.add(compilationPanel, BorderLayout.NORTH);
        
        JPanel returnType = new JPanel(new GridLayout(0, 2));
        returnType.setBorder(BorderFactory.createTitledBorder("Return type"));
        for (Enumeration<?> e = m_returnTypeButtonGroup.getElements(); e
                .hasMoreElements();) {
            returnType.add((AbstractButton)e.nextElement());
        }
        returnTypeAndCompilation.add(returnType, BorderLayout.CENTER);
        
        southPanel.add(returnTypeAndCompilation);
        finalPanel.add(centerPanel, BorderLayout.CENTER);
        finalPanel.add(southPanel, BorderLayout.SOUTH);
        return finalPanel;
    }

    /**
     * Renderer that will display the rowindex and rowkey with different
     * background.
     */
    private static class ListRenderer extends DataColumnSpecListCellRenderer {
        /**
         * {@inheritDoc}
         */
        @Override
        public Component getListCellRendererComponent(final JList list,
                final Object value, final int index, final boolean isSelected,
                final boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value,
                    index, isSelected, cellHasFocus);
            if (value instanceof String) {
                c.setFont(list.getFont().deriveFont(Font.ITALIC));
            }
            return c;
        }
    }
}
