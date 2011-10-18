/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2011
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
 *  propagated with or for interoperation with KNIME. The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ------------------------------------------------------------------------
 *
 * History
 *   04.10.2011 (hofer): created
 */
package org.knime.base.node.preproc.stringmanipulation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.swing.tree.DefaultMutableTreeNode;

import org.knime.base.node.preproc.stringmanipulation.manipulator.CapitalizeDelimManipulator;
import org.knime.base.node.preproc.stringmanipulation.manipulator.CapitalizeManipulator;
import org.knime.base.node.preproc.stringmanipulation.manipulator.IndexOfManipulator;
import org.knime.base.node.preproc.stringmanipulation.manipulator.LowerCaseManipulator;
import org.knime.base.node.preproc.stringmanipulation.manipulator.RemoveCharacterManipulator;
import org.knime.base.node.preproc.stringmanipulation.manipulator.RemoveDuplicatesManipulator;
import org.knime.base.node.preproc.stringmanipulation.manipulator.RemoveSpecificCharacterManipulator;
import org.knime.base.node.preproc.stringmanipulation.manipulator.StringManipulator;
import org.knime.base.node.preproc.stringmanipulation.manipulator.SubstringManipulator;
import org.knime.base.node.preproc.stringmanipulation.manipulator.UpperCaseManipulator;
import org.knime.core.util.FileUtil;

/**
 *
 * @author Heiko Hofer
 */
public final class StringManipulatorProvider {
    /**
     * The display name of the category with all string manipulators.
     */
    static final String ALL_CATEGORY = "All";
    private Map<String, Collection<StringManipulator>> m_manipulators;
    private File m_jarFile;


    private static StringManipulatorProvider provider;

    /**
     * Get default shared instance.
     * @return default StringManipulatorProvider
     */
    public static StringManipulatorProvider getDefault() {
        if (null == provider) {
            provider = new StringManipulatorProvider();
        }
        return provider;
    }

    /**
     * prevent instantiation.
     */
    private StringManipulatorProvider() {
        m_manipulators = new LinkedHashMap<String,
                                    Collection<StringManipulator>>();

        Collection<StringManipulator> manipulators =
            new TreeSet<StringManipulator>(new Comparator<StringManipulator>() {

                @Override
                public int compare(final StringManipulator o1,
                        final StringManipulator o2) {
                    return o1.getDisplayName().compareTo(o2.getDisplayName());
                }
            });

        manipulators.add(new CapitalizeDelimManipulator());
        manipulators.add(new CapitalizeManipulator());
        manipulators.add(new IndexOfManipulator());
        manipulators.add(new LowerCaseManipulator());
        manipulators.add(new RemoveCharacterManipulator());
        manipulators.add(new RemoveDuplicatesManipulator());
        manipulators.add(new RemoveSpecificCharacterManipulator());
        manipulators.add(new SubstringManipulator());
        manipulators.add(new UpperCaseManipulator());

        Set<String> categories = new TreeSet<String>();
        for (StringManipulator m : manipulators) {
            String category = m.getCategory();
            if (category.equals(ALL_CATEGORY)) {
                throw new IllegalStateException(
                        "The category \"All\" is not allowed.");
            }
            categories.add(category);
        }
        m_manipulators.put(ALL_CATEGORY, manipulators);
        for (String category : categories) {
            m_manipulators.put(category, new ArrayList<StringManipulator>());
        }
        for (StringManipulator m : manipulators) {
            Collection<StringManipulator> list =
                m_manipulators.get(m.getCategory());
            list.add(m);
        }

    }

    /**
     * Get all categories.
     *
     * @return the categories
     */
    public Object[] getCategories() {
        return m_manipulators.keySet().toArray();
    }


    /**
     * Get the {@link StringManipulator}s in the given category.
     * @param category a category as given by getCategories()
     * @return the {@link StringManipulator}s in the given category
     */
    public Collection<StringManipulator> getManipulators(
            final String category) {
        return m_manipulators.get(category);
    }

    public File getJarFile() throws IOException {
        if (m_jarFile == null) {
            File tempClassPathDir = FileUtil
                    .createTempDir("knime_stringmanipulation");
            tempClassPathDir.deleteOnExit();
            m_jarFile = new File(tempClassPathDir.getPath()
                    + File.separator + "manipulators.jar");
            m_jarFile.deleteOnExit();
            FileOutputStream out = new FileOutputStream(m_jarFile);
            JarOutputStream jar = new JarOutputStream(out);

            Collection<Object> classes = new ArrayList<Object>();
            classes.add(StringManipulator.class);
            classes.addAll(m_manipulators.get(ALL_CATEGORY));
            // create tree structure for classes
            DefaultMutableTreeNode root = createTree(classes);
            try {
                createJar(root, jar, null);
            } catch (IOException ioe) {
                throw ioe;
            } finally {
                jar.close();
                out.close();
            }

        }
        return m_jarFile;
    }

    private DefaultMutableTreeNode createTree(
    		final Collection<? extends Object> classes) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("build");
        for (Object o : classes) {
            Class<?> cl = o instanceof Class ? (Class<?>)o : o.getClass();
            Package pack = cl.getPackage();
            DefaultMutableTreeNode curr = root;
            for (String p : pack.getName().split("\\.")) {
                DefaultMutableTreeNode child = getChild(curr, p);
                if (null == child) {
                    DefaultMutableTreeNode h = new DefaultMutableTreeNode(p);
                    curr.add(h);
                    curr = h;
                } else {
                    curr = child;
                }
            }
            curr.add(new DefaultMutableTreeNode(cl));
        }

        return root;
    }


    private DefaultMutableTreeNode getChild(final DefaultMutableTreeNode curr,
            final String p) {
        for (int i = 0; i < curr.getChildCount(); i++) {
            DefaultMutableTreeNode child =
                (DefaultMutableTreeNode)curr.getChildAt(i);
            if (child.getUserObject().toString().equals(p)) {
                return child;
            }
        }
        return null;
    }


    private void createJar(final DefaultMutableTreeNode node,
            final JarOutputStream jar,
            final String path) throws IOException {
        Object o = node.getUserObject();
        if (o instanceof String) {
        	// folders must end with a "/"
            String subPath = null == path ? "" : (path + (String)o + "/");
            if (path != null) {
                JarEntry je = new JarEntry(subPath);
                jar.putNextEntry(je);
                jar.flush();
                jar.closeEntry();
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                DefaultMutableTreeNode child =
                    (DefaultMutableTreeNode) node.getChildAt(i);
                createJar(child, jar, subPath);
            }
        } else {
            Class<?> cl = (Class<?>)o;
            String className = cl.getSimpleName();
            className = className.concat(".class");
            JarEntry entry = new JarEntry(path + className);
            jar.putNextEntry(entry);

            ClassLoader loader = cl.getClassLoader();
            InputStream inStream = loader.getResourceAsStream(
                    cl.getName().replace('.', '/') + ".class");

            FileUtil.copy(inStream, jar);
            inStream.close();
            jar.flush();
            jar.closeEntry();

        }
    }

}
