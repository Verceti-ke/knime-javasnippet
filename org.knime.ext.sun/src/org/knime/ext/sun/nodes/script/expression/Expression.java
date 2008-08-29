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
package org.knime.ext.sun.nodes.script.expression;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.knime.core.node.NodeLogger;

import com.sun.tools.javac.Main;


/**
 * An expression contains the java code snippet including imports, header,
 * method definition etc. Once generated, an ExpressionInstance is available
 * using the {@link #getInstance()} method on which calculations can be carried
 * out.
 */
public class Expression implements Serializable {
    private static final NodeLogger LOGGER = NodeLogger
            .getLogger(Expression.class);
    
    /** Source of a field. */
    public enum FieldType {
        /** Represents a column value. */ 
        Column,
        /** Represents a scope variable. */
        Variable,
        /** Represents a table column, e.g. total row count. */
        TableConstant
    }

    /** These imports are put in the import section of the source file. */
    private static final Collection<String> IMPORTS = Arrays
            .asList(new String[]{"java.text.*", "java.util.*", "java.io.*",
                    "java.net.*"});

    /** Temp file to use. Must end with .java, passed in constructor. */
    private final File m_javaFile;

    /** Created class file, will be created and deleted in this class. */
    private File m_classFile;

    /**
     * Properties, i.e. fields in the source file with their corresponding
     * class.
     */
    private final Map<InputField, ExpressionField> m_fieldMap;

    /** The compiled class for the instance of the expression. */
    private final Class<?> m_compiled;

    /**
     * Constructor for an expression with fields.
     * 
     * @param body the expression body (Java)
     * @param fieldMap the property-names mapped to types, i.e. class name of
     *             the field that is available to the evaluated expression
     * @param rType the return type of the expression (i.e. Integer.class,
     *            Double.class, String.class)
     * @param tempFile the temp file to write the java source to
     * @throws CompilationFailedException when the expression couldn't be
     *             compiled
     * @throws IllegalArgumentException if the map contains <code>null</code>
     *             elements
     */
    public Expression(final String body, 
            final Map<InputField, ExpressionField> fieldMap,
            final Class<?> rType, final File tempFile)
            throws CompilationFailedException {
        if (!tempFile.getName().endsWith(".java")) {
            throw new IllegalArgumentException("No java file: "
                    + tempFile.getName());
        }
        m_javaFile = tempFile;
        m_fieldMap = fieldMap;
        m_compiled = createClass(body, rType);
    }

    /* Called from the constructor. */
    private Class<?> createClass(final String body, final Class<?> rType)
            throws CompilationFailedException {

        String javaAbsoluteName;
        String javaClassName;
        String classAbsoluteName;
        try {
            String name = m_javaFile.getName();
            javaClassName = name.substring(0, name.length() - ".java".length());
            javaAbsoluteName = m_javaFile.getAbsolutePath();
            // Generate the well known source of the Expression
            String source = generateSource(javaClassName, body, rType);
            BufferedWriter w = new BufferedWriter(new FileWriter(m_javaFile));
            w.write(source);
            w.close();
        } catch (IOException ioe) {
            CompilationFailedException c = new CompilationFailedException(
                    "Unable to write java temp file.");
            c.initCause(ioe);
            throw c;
        }
        final StringWriter logString = new StringWriter();
        final PrintWriter log = new PrintWriter(logString);
        // compile the classes
        if (Main.compile(new String[]{javaAbsoluteName}, log) != 0) {
            throw new CompilationFailedException("Unable to compile \"" + body
                    + "\":\n" + logString.toString());
        }
        classAbsoluteName = javaAbsoluteName.substring(
                0, javaAbsoluteName.length() - ".java".length()) + ".class";
        m_classFile = new File(classAbsoluteName);
        assert (m_classFile.exists());
        m_classFile.deleteOnExit();
        Class<?> compiled;
        try {
            URL[] urls = new URL[]{m_classFile.getParentFile().toURI().toURL()};
            ClassLoader load = URLClassLoader.newInstance(urls);
            compiled = load.loadClass(javaClassName);
        } catch (MalformedURLException mue) {
            CompilationFailedException c = new CompilationFailedException(
                    "Can't determine URL of class file " + classAbsoluteName);
            c.initCause(mue);
            throw c;
        } catch (ClassNotFoundException cfe) {
            StringBuilder addInfo = new StringBuilder("(Class file ");
            addInfo.append(m_classFile.getAbsolutePath());
            if (m_classFile.exists()) {
                addInfo.append(" does exist - ");
                addInfo.append(m_classFile.length());
                addInfo.append(" bytes");
            } else {
                addInfo.append(" does not exist");
            }
            addInfo.append(")");
            CompilationFailedException c = new CompilationFailedException(
                    "Can't load class file " + classAbsoluteName
                    + addInfo.toString());
            c.initCause(cfe);
            throw c;
        }
        return compiled;
    }

    /**
     * Generates a new instance of the compiled class.
     * 
     * @return a new expression instance that wraps the compiled source
     * @throws InstantiationException if the compiled class can't be
     *             instantiated
     */
    public ExpressionInstance getInstance() throws InstantiationException {
        try {
            return new ExpressionInstance(
                    m_compiled.newInstance(), m_fieldMap);
        } catch (IllegalAccessException iae) {
            LOGGER.error("Unexpected IllegalAccessException occured", iae);
            throw new InternalError();
        }
    }
    
    /**
     * @return the fieldMap
     */
    public Map<InputField, ExpressionField> getFieldMap() {
        return m_fieldMap;
    }
    
    /*
     * Creates the source for given expression classname, body & properties.
     */
    private String generateSource(final String filename, final String body,
            final Class<?> rType) {
        String copy = body;
        StringBuilder buffer = new StringBuilder(4096);

        /* Generate header */
        // Here comes the class
        for (String imp : IMPORTS) {
            buffer.append("import ");
            buffer.append(imp);
            buffer.append(";");
            buffer.append("\n");
        }

        buffer.append("public class " + filename + " {");
        buffer.append("\n");
        buffer.append("\n");

        /* Add the source fields */
        for (ExpressionField type : m_fieldMap.values()) {
            buffer.append("  public ");
            buffer.append(type.getFieldClass().getSimpleName());
            buffer.append(" " + type.getExpressionFieldName() + ";");
            buffer.append("\n");
        }
        buffer.append("\n");

        /* Add body */
        String cast;
        if (rType.equals(Double.class)) {
            cast = "(double)(";
        } else if (rType.equals(Integer.class)) {
            cast = "(int)(";
        } else if (rType.equals(String.class)) {
            cast = "\"\" + (";
        } else {
            cast = "(" + rType.getName() + ")(";
        }

        // And the evaluation method
        buffer.append("  public Object internalEvaluate() {");
        buffer.append("\n");

        int instructions = copy.lastIndexOf(';');
        if (instructions >= 0) {
            buffer.append("    " + copy.substring(0, instructions + 1));
            copy = copy.substring(instructions + 1);
        }
        // .. and the (last and final) expression which is 'objectified'
        buffer.append("\n");
        buffer.append("    return objectify(" + cast + copy + "));\n");
        buffer.append("  }\n\n");
        buffer.append(OBJECTIVER);

        /* Add footer */
        buffer.append('}');
        buffer.append("\n");
        return buffer.toString();
    }
    
    /** Object that pairs the name of the field used in the temporarily created
     * java class with the class of that field. */
    public static final class ExpressionField {
        private final String m_expressionFieldName;
        private final Class<?> m_fieldClass;
        
        /** @param expressionFieldName The field name
         * @param fieldClass The class representing the field.
         */
        public ExpressionField(final String expressionFieldName,
                final Class<?> fieldClass) {
            if (expressionFieldName == null || fieldClass == null) {
                throw new NullPointerException("Arg must not be null");
            }
            m_expressionFieldName = expressionFieldName;
            m_fieldClass = fieldClass;
        }
        
        /** @return the expressionFieldName */
        public String getExpressionFieldName() {
            return m_expressionFieldName;
        }
        
        /** @return the field class. */
        public Class<?> getFieldClass() {
            return m_fieldClass;
        }
    }
    
    /** Pair of original column name or scope variable identifier with a 
     * enum type indicating the source. */
    public static final class InputField {
        
        private final String m_colOrVarName;
        private final FieldType m_fieldType;

        /** @param colOrVarName The name of the (original!) field 
         * @param fieldType The type of the source.
         */
        public InputField(final String colOrVarName, 
                final FieldType fieldType) {
            if (colOrVarName == null || fieldType == null) {
                throw new NullPointerException("Arg is null");
            }
            m_colOrVarName = colOrVarName;
            m_fieldType = fieldType;
        }
        
        /** {@inheritDoc} */
        @Override
        public String toString() {
            return m_fieldType + " \"" + m_colOrVarName + "\"";
                
        }
        
        /**
         * @return the fieldType
         */
        public FieldType getFieldType() {
            return m_fieldType;
        }
        
        /**
         * @return the colOrVarName
         */
        public String getColOrVarName() {
            return m_colOrVarName;
        }
        
        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return m_colOrVarName.hashCode() + m_fieldType.hashCode();
        }
        
        /** {@inheritDoc} */
        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof InputField)) {
                return false;
            }
            InputField f = (InputField)obj;
            return f.m_fieldType.equals(m_fieldType) 
                && f.m_colOrVarName.equals(m_colOrVarName);
        }
    }

    /** String containing the objectivy method for compilation. */
    private static final String OBJECTIVER = 
        "  protected final Byte objectify(byte b) {\n"
            + "    return new Byte(b);\n"
            + "  }\n\n"
            + "  protected final Character objectify(char c) {\n"
            + "    return new Character(c);\n"
            + "  }\n\n"
            + "  protected final Double objectify(double d) {\n"
            + "    return new Double(d);\n"
            + "  }\n\n"
            + "  protected final Float objectify(float d) {\n"
            + "    return new Float(d);\n"
            + "  }\n\n"
            + "  protected final Integer objectify(int i) {\n"
            + "    return new Integer(i);\n"
            + "  }\n\n"
            + "  protected final Long objectify(long l) {\n"
            + "    return new Long(l);\n"
            + "  }\n\n"
            + "  protected final Object objectify(Object o) {\n"
            + "    return o;\n"
            + "  }\n\n"
            + "  protected final Short objectify(short s) {\n"
            + "    return new Short(s);\n"
            + "  }\n\n"
            + "  protected final Boolean objectify(boolean b) {\n"
            + "    return new Boolean(b);\n" + "  }\n\n";

}
