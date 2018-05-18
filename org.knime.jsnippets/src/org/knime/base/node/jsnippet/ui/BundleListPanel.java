/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME GmbH, Konstanz, Germany
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
 * History
 *   13.09.2017 (Jonathan Hale): created
 */
package org.knime.base.node.jsnippet.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.eclipse.core.runtime.Platform;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.util.filter.ArrayListModel;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;

/**
 * Panel for adding Bundles to the Java Snippet node for compilation.
 *
 * @author Jonathan Hale, KNIME, Konstanz, Germany
 * @since 3.5
 * @noextend This class is not intended to be subclassed by clients.
 * @noinstantiate This class is not intended to be instantiated by clients.
 * @noreference This class is not intended to be referenced by clients.
 */
public class BundleListPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Entry in the Bundle List */
    static class BundleListEntry {
        /** Name of the bundle */
        final String name;

        final Version installedVersion;
        final Version savedVersion;

        /**
         * Constructor
         *
         * @param name
         * @param installedVersion
         * @param savedVersion Version the bundle was saved with, in case it significantly differs from the installed
         *            version.
         */
        public BundleListEntry(final String name, final Version installedVersion, final Version savedVersion) {
            this.name = name;
            this.installedVersion = installedVersion;
            this.savedVersion = savedVersion;
        }

        @Override
        public String toString() {
            return this.name + " " + this.installedVersion.toString();
        }

        /** Whether this bundle is installed in the current eclipse runtime */
        public boolean exists() {
            return installedVersion != null;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof String) {
                return this.name.equals(obj);
            } else if (obj instanceof BundleListEntry) {
                return this.name.equals(((BundleListEntry)obj).name);
            }
            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            return this.name.hashCode();
        }
    }

    class BundleListEntryRenderer extends DefaultListCellRenderer {
        /**
         * {@inheritDoc}
         */
        @Override
        public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index,
            final boolean isSelected, final boolean cellHasFocus) {
            final BundleListEntry e = ((BundleListEntry)value);
            final Component c = super.getListCellRendererComponent(list, e.name, index, isSelected, cellHasFocus);
            if (!e.exists()) {
                c.setForeground(Color.RED);
                if (c instanceof JLabel) {
                    ((JLabel)c).setToolTipText("Bundle is not installed.");
                }
            } else if (e.savedVersion != null) {
                c.setForeground(Color.YELLOW);
                if (c instanceof JLabel) {
                    ((JLabel)c).setToolTipText(String.format("Installed version (%s) is different than the version when this workflow was saved (%s).", e.installedVersion, e.savedVersion));
                }
            }

            return c;
        }
    }

    /* Filterable list model containing all available bundles */
    final ArrayListModel<BundleListEntry> m_listModel = new ArrayListModel<>();

    /* List displaying all available bundles */
    final JList<BundleListEntry> m_list = new JList<>(m_listModel);

    /* Field for filtering the bundle list */
    final JTextField m_filterField = new JTextField();

    /* All available bundle names */
    final static ArrayList<String> bundleNames = new ArrayList<>();

    /* Find all available bundles */
    private static void initBundleNames() {
        if (!bundleNames.isEmpty()) {
            return;
        }

        final BundleContext ctx = FrameworkUtil.getBundle(BundleListPanel.class).getBundleContext();
        for (final Bundle bundle : ctx.getBundles()) {
            bundleNames.add(bundle.getSymbolicName() + " " + bundle.getVersion().toString());
        }

        Collections.sort(bundleNames);
    }

    /* List model which filters bundleNames acording to a search string */
    static final class FilterableListModel extends AbstractListModel<String> {

        private static final long serialVersionUID = 1L;

        private final List<String> m_unfiltered;

        private List<String> m_filtered;

        private String m_filter = "";

        /**
         * Constructor
         *
         * @param unfiltered Unfiltered list of elements
         */
        public FilterableListModel(final List<String> unfiltered) {
            m_filtered = m_unfiltered = unfiltered;
        }

        @Override
        public int getSize() {
            return m_filtered.size();
        }

        @Override
        public String getElementAt(final int index) {
            return m_filtered.get(index);
        }

        /**
         * Set the string with which to filter the elements of this list.
         *
         * @param filter Filter string
         */
        public synchronized void setFilter(final String filter) {
            if (m_filter.equals(filter)) {
                return;
            }
            if (filter.startsWith(m_filter)) {
                // the most common use case will be a list gradually refined by user typing more characters
                m_filtered = m_filtered.stream().filter(s -> s.contains(filter)).collect(Collectors.toList());
            } else if (filter.isEmpty()) {
                m_filtered = m_unfiltered;
            } else {
                m_filtered = m_unfiltered.stream().filter(s -> s.contains(filter)).collect(Collectors.toList());
            }
            m_filter = filter;
            this.fireContentsChanged(this, 0, bundleNames.size());
        }
    }

    /**
     * Constructor
     */
    public BundleListPanel() {
        super(new BorderLayout());

        initBundleNames();

        m_list.setCellRenderer(new BundleListEntryRenderer());
        add(new JScrollPane(m_list), BorderLayout.CENTER);

        final JPanel northPane = new JPanel(new FlowLayout());
        northPane.add(new JLabel("Add bundles from the current eclipse environment as dependencies."));
        add(northPane, BorderLayout.NORTH);

        final JButton addBundleButton = new JButton("Add Bundle");
        addBundleButton.addActionListener(e -> openAddBundleDialog());

        final JButton removeBundleButton = new JButton("Remove");
        removeBundleButton.addActionListener(e -> removeSelectedBundles());

        final JPanel southPane = new JPanel(new FlowLayout());
        southPane.add(addBundleButton);
        southPane.add(removeBundleButton);
        add(southPane, BorderLayout.SOUTH);
    }

    /**
     * Dialog that displays a list of available bundles for selection.
     *
     * @author Jonathan Hale, KNIME GmbH, Konstanz, Germany
     */
    class AddBundleDialog extends JDialog {
        private static final long serialVersionUID = 1L;

        final JList<String> m_bundleList;

        final FilterableListModel m_bundleModel = new FilterableListModel(bundleNames);

        public AddBundleDialog(final Window window, final String title) {
            final int width = 480;
            final int height = 360;

            final Point p = window.getLocationOnScreen();
            final Dimension d = getSize();
            setLocation(p.x + (d.width - width) / 2, p.y + (d.height - height) / 2);

            if (KNIMEConstants.KNIME16X16 != null) {
                setIconImage(KNIMEConstants.KNIME16X16.getImage());
            }

            final JPanel pane = new JPanel(new BorderLayout());

            final JPanel northPane = new JPanel(new GridLayout(2, 1));
            northPane.add(new JLabel("Double-click bundle to add it together with its dependencies."));

            m_filterField.requestFocusInWindow();
            northPane.add(m_filterField);
            pane.add(northPane, BorderLayout.NORTH);

            m_bundleList = new JList<>(m_bundleModel);
            final JScrollPane scrollPane = new JScrollPane(m_bundleList);
            scrollPane.setPreferredSize(new Dimension(width, height));
            pane.add(scrollPane, BorderLayout.CENTER);

            m_bundleList.addMouseListener(new MouseListener() {

                @Override
                public void mouseReleased(final MouseEvent e) {
                }

                @Override
                public void mousePressed(final MouseEvent e) {
                }

                @Override
                public void mouseExited(final MouseEvent e) {
                }

                @Override
                public void mouseEntered(final MouseEvent e) {
                }

                @Override
                public void mouseClicked(final MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        // Double click closes the dialog
                        final String bundleName = m_bundleList.getSelectedValue();
                        addBundle(bundleName);

                        setVisible(false);
                        dispose();
                    }
                }
            });
            m_bundleList.addKeyListener(new KeyListener() {

                @Override
                public void keyTyped(final KeyEvent e) {
                }

                @Override
                public void keyReleased(final KeyEvent e) {
                }

                @Override
                public void keyPressed(final KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        addBundles(m_bundleList.getSelectedValuesList());
                        setVisible(false);
                        dispose();
                    }
                }
            });

            m_filterField.getDocument().addDocumentListener(new DocumentListener() {

                @Override
                public void removeUpdate(final DocumentEvent e) {
                    update(e);
                }

                @Override
                public void insertUpdate(final DocumentEvent e) {
                    update(e);
                }

                @Override
                public void changedUpdate(final DocumentEvent e) {
                    update(e);
                }

                void update(final DocumentEvent e) {
                    try {
                        final Document doc = e.getDocument();
                        m_bundleModel.setFilter(doc.getText(0, doc.getLength()));
                    } catch (BadLocationException e1) {
                        // Will never happen
                    }
                }
            });

            add(pane);
        }
    }

    /* Opens the "Add Bundle" dialog */
    AddBundleDialog openAddBundleDialog() {
        final AddBundleDialog frame = new AddBundleDialog(SwingUtilities.getWindowAncestor(this), "Add Bundle");
        frame.pack();
        frame.setVisible(true);

        return frame;
    }

    /* Remove all bundles currently selected in list */
    void removeSelectedBundles() {
        m_listModel.removeAll(m_list.getSelectedValuesList());
    }

    /**
     * @return All added bundles.
     */
    public String[] getBundles() {
        final String[] bundles = new String[m_listModel.getSize()];

        for (int i = 0; i < m_listModel.getSize(); ++i) {
            bundles[i] = m_listModel.get(i).toString();
        }

        return bundles;
    }

    /**
     * @param bundles
     */
    public void setBundles(final String[] bundles) {
        m_listModel.clear();

        addBundles(Arrays.asList(bundles));
    }

    /**
     * Add a bundle to the bundle list.
     *
     * @param bundleName Name of the bundle to add.
     * @return true if adding was successful, false if bundle with given name was not found.
     */
    public boolean addBundle(final String bundleName) {
        final BundleListEntry e = makeBundleListEntry(bundleName);
        if (e == null) {
            return false;
        }

        if (m_listModel.contains(e)) {
            return false;
        }

        //final Set<String> bundleNameSet = new HashSet<String>();
        m_listModel.add(e);

        //for (final String bn : bundleNameSet) {
        //    listToAddTo.addElement(bn);
        //}

        return true;
    }

    /**
     * Add given bundle names to list. Duplicates and null names are skipped.
     *
     * @param list The bundle names to add
     */
    public void addBundles(final List<String> list) {
        final LinkedHashSet<BundleListEntry> entries = new LinkedHashSet<>(list.size());
        for (final String b : list) {
            if (b == null) {
                continue;
            }

            final BundleListEntry e = makeBundleListEntry(b);
            if (m_listModel.contains(e.name)) {
                continue;
            }

            entries.add(e);
        }

        m_listModel.addAll(entries);
    }

    /* Create a BundleListEntry from bundleName */
    private static BundleListEntry makeBundleListEntry(final String bundleName) {
        if (bundleName == null) {
            return null;
        }

        final String[] split = bundleName.split(" ");
        final String nameWithoutVersion = split[0];
        final Bundle firstBundle = Platform.getBundle(nameWithoutVersion);

        if (firstBundle != null) {
            final Version installedVersion = firstBundle.getVersion();
            Version savedVersion = null;
            if(split.length > 1) {
                final Version v = Version.parseVersion(split[1]);

                // check whether the versions differ up to minor version.
                final boolean versionsDiffer = installedVersion.getMajor() != v.getMajor()
                    || installedVersion.getMinor() != v.getMinor();
                if(versionsDiffer) {
                    savedVersion = v;
                }
            }

            return new BundleListEntry(firstBundle.getSymbolicName(), installedVersion, savedVersion);
        } else {
            return new BundleListEntry(nameWithoutVersion, null, null);
        }
    }

    /**
     * @return Model of main List
     */
    public ListModel<BundleListEntry> getListModel() {
        return m_listModel;
    }
}
