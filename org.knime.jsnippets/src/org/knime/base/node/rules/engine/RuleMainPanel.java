/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2013
 *  University of Konstanz, Germany and
 *  KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * Created on 2013.04.25. by Gabor
 */
package org.knime.base.node.rules.engine;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Date;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.text.BadLocationException;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.IconRowHeader;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.knime.base.node.rules.engine.manipulator.InfixManipulator;
import org.knime.base.node.rules.engine.manipulator.PrefixUnaryManipulator;
import org.knime.base.node.util.JSnippetPanel;
import org.knime.base.node.util.KnimeCompletionProvider;
import org.knime.base.node.util.KnimeSyntaxTextArea;
import org.knime.base.node.util.ManipulatorProvider;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeLogger;

/**
 * The main panel (manipulators, columns, flow variables and the editor) of the rule engine node dialogs.
 *
 * @author Gabor Bakos
 * @since 2.8
 */
@SuppressWarnings("serial")
class RuleMainPanel extends JSnippetPanel {
    private static final NodeLogger LOGGER = NodeLogger.getLogger(RuleMainPanel.class);

    private KnimeSyntaxTextArea m_textEditor;

    private Gutter m_gutter;

    private static class ToggleRuleAction extends AbstractAction {
        private static final long serialVersionUID = 5930758516767278299L;

        private static class LinePosition extends ActionEvent {
            private static final long serialVersionUID = -7627500390929718724L;

            private final int m_lineNumber;

            /**
             * @param source
             * @param id
             * @param command
             * @param modifiers
             * @param lineNumber Line number starting from {@code 0}.
             */
            @SuppressWarnings("hiding")
            public LinePosition(final Object source, final int id, final String command, final int modifiers,
                                final int lineNumber) {
                super(source, id, command, modifiers);
                this.m_lineNumber = lineNumber;
            }

            /**
             * @return the lineNumber
             */
            public int getLineNumber() {
                return m_lineNumber;
            }
        }

        private final RTextArea m_textArea;

        /**
         * @param textArea The {@link RTextArea} where the toggle action is applied.
         */
        public ToggleRuleAction(final RTextArea textArea) {
            this("", textArea);
        }

        public ToggleRuleAction(final String name, final RTextArea textArea) {
            this(name, null, textArea);
        }

        public ToggleRuleAction(final String name, final Icon icon, final RTextArea textArea) {
            super(name, icon);
            m_textArea = textArea;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionPerformed(final ActionEvent e) {
            Object source = e.getSource();
            RTextArea textArea;
            if (source instanceof RTextArea) {
                textArea = (RTextArea)source;
            } else {
                textArea = m_textArea;
            }
            try {
                if (e instanceof LinePosition) {
                    LinePosition linePosition = (LinePosition)e;
                    int line = linePosition.getLineNumber();
                    toggle(textArea, line);
                } else {
                    int line = textArea.getLineOfOffset(textArea.getLineStartOffsetOfCurrentLine());
                    toggle(textArea, line);
                }
            } catch (BadLocationException ex) {
                LOGGER.debug(ex.getMessage(), ex);
            }
        }

        /**
         * @param textArea
         * @param line
         */
        private void toggle(final RTextArea textArea, final int line) {
            int lineStart;
            try {
                lineStart = textArea.getLineStartOffset(line);
                String ruleText = textArea.getText().substring(lineStart, textArea.getLineEndOffset(line));
                if (RuleSupport.isComment(ruleText)) {
                    int l = 0;
                    while (ruleText.charAt(l) == '/') {
                        ++l;
                    }
                    textArea.replaceRange("", lineStart, lineStart + l);
                } else {
                    textArea.insert("//", lineStart);
                }
            } catch (BadLocationException e1) {
                LOGGER.debug(e1.getMessage(), e1);
            }
        }
    }

    /**
     * Constructs the main panel.
     *
     * @param manipulatorProvider The {@link ManipulatorProvider}.
     * @param completionProvider The {@link KnimeCompletionProvider}.
     */
    public RuleMainPanel(final ManipulatorProvider manipulatorProvider, final KnimeCompletionProvider completionProvider) {
        this(manipulatorProvider, completionProvider, true);
    }

    /**
     * Constructs the main panel.
     *
     * @param manipulatorProvider The {@link ManipulatorProvider}.
     * @param completionProvider The {@link KnimeCompletionProvider}.
     * @param showColumns Show the columns panel, or hide it?
     */
    public RuleMainPanel(final ManipulatorProvider manipulatorProvider,
                         final KnimeCompletionProvider completionProvider, final boolean showColumns) {
        super(manipulatorProvider, completionProvider, showColumns);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JComponent createEditorComponent() {
        final RSyntaxTextArea textArea = m_textEditor = new KnimeSyntaxTextArea(20, 60);
        // An AutoCompletion acts as a "middle-man" between a text component
        // and a CompletionProvider. It manages any options associated with
        // the auto-completion (the popup trigger key, whether to display a
        // documentation window along with completion choices, etc.). Unlike
        // CompletionProviders, instances of AutoCompletion cannot be shared
        // among multiple text components.
        AutoCompletion ac = new AutoCompletion(getCompletionProvider());
        ac.setShowDescWindow(true);

        ac.install(textArea);
        setExpEdit(textArea);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PrintStream stringWriter = new PrintStream(out, false, "UTF-8");
            PrintStream oldWriter = System.err;
            System.setErr(stringWriter);
            textArea.setSyntaxEditingStyle(RuleParser.SYNTAX_STYLE_RULE);
            System.setErr(oldWriter);
        } catch (UnsupportedEncodingException ex) {
            LOGGER.coding("Strange, encoding UTF-8 is not known", ex);
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        LOGGER.debug(new String(out.toByteArray(), Charset.forName("UTF-8")));

        textArea.getPopupMenu().add(new ToggleRuleAction("Toggle comment", textArea));
        RTextScrollPane textScrollPane = new RTextScrollPane(textArea);
        textScrollPane.setLineNumbersEnabled(true);
        textScrollPane.setIconRowHeaderEnabled(true);
        m_gutter = textScrollPane.getGutter();
        m_gutter.setBookmarkingEnabled(true);
        m_gutter.setBookmarkIcon(KNIMEConstants.KNIME16X16);
        addRowHeaderMouseListener(new MouseAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseClicked(final MouseEvent e) {
                    if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                        try {
                            new ToggleRuleAction(textArea).actionPerformed(new ToggleRuleAction.LinePosition(
                                    textArea, (int)(new Date().getTime() & 0x7fffffff), "toggle comment", e
                                            .getModifiers(), textArea.getLineOfOffset(textArea.viewToModel(e
                                            .getPoint()))));
                        } catch (BadLocationException e1) {
                            LOGGER.debug(e1.getMessage(), e1);
                        }
                    }
                }
        });
        return textScrollPane;
    }

    /**
     * Adds a {@link MouseListener} to the row header.
     *
     * @param listener A {@link MouseListener} to handle clicks on the row header.
     */
    public void addRowHeaderMouseListener(final MouseListener listener) {
        final IconRowHeader rowHeader = Util.findComponent(m_gutter.getComponents(), IconRowHeader.class);
        if (rowHeader != null) {
            rowHeader.addMouseListener(listener);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onSelectionInManipulatorList(final Object selected) {
        if (selected instanceof InfixManipulator) {
            InfixManipulator infix = (InfixManipulator)selected;
            //String selectedString = m_textEditor.getSelectedText();
            String textToInsert = infix.getName() + " ";
            try {
                if (m_textEditor.getCaretPosition() == 0 || m_textEditor.getText().isEmpty() || m_textEditor.getText(m_textEditor.getCaretPosition(), 1).charAt(0) != ' ') {
                    textToInsert = " " + textToInsert;
                }
            } catch (BadLocationException e) {
                LOGGER.coding("Not fatal error, but should not happen, requires no action.", e);
            }
            m_textEditor.insert(textToInsert, m_textEditor.getCaretPosition());
            m_textEditor.requestFocus();
        } else if (selected instanceof PrefixUnaryManipulator) {
            PrefixUnaryManipulator prefix = (PrefixUnaryManipulator)selected;
            m_textEditor.replaceSelection(prefix.getName() + " ");

            m_textEditor.requestFocus();
        } else {
            super.onSelectionInManipulatorList(selected);
        }
    }

    /**
     * @return the textEditor
     */
    public KnimeSyntaxTextArea getTextEditor() {
        return m_textEditor;
    }

    /**
     * @return the gutter
     */
    public Gutter getGutter() {
        return m_gutter;
    }
}
