/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip.xml;

import com.thoughtworks.xstream.annotations.*;
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This XStream class describes the database and how to dump it.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@XStreamAlias("delving-rdbms-profile")
public class RelationalProfile {
    @XStreamAsAttribute
    public String name;

    @XStreamImplicit
    public List<Table> tables = new ArrayList<Table>();

    public Table rootTable() {
        Table rootTable = null;
        for (Table table : tables) {
            if (table.parentTable != null && table.parentTable.isEmpty()) {
                if (rootTable != null) throw new RuntimeException("Multiple root tables not permitted!");
                rootTable = table;
            }
        }
        if (rootTable == null) throw new RuntimeException("One root table must be marked with parentTable=\"\"");
        return rootTable;
    }

    public List<Table> childTables(Table parent) {
        List<Table> childTables = new ArrayList<Table>();
        for (Table table : tables) {
            if (table.parentTable != null && table.parentTable.equals(parent.name)) childTables.add(table);
        }
        return childTables;
    }

    public Table addTable(String name) {
        Table table = new Table();
        table.name = name;
        tables.add(table);
        return table;
    }

    public void resolve() {
        for (Table table : tables) {
            table.resolve(this);
        }
    }

    @XStreamAlias("table")
    public static class Table {
        @XStreamAsAttribute
        public String name;

        @XStreamAsAttribute
        public String parentTable;

        @XStreamAsAttribute
        public boolean cached;

        public String query;

        @XStreamImplicit
        public List<Column> columns = new ArrayList<Column>();

        @XStreamOmitField
        public Table parent;

        @XStreamOmitField
        public Column linkColumn;

        @XStreamOmitField
        public Map<String, Map<String, String>> cache;

        public Column addColumn(String name) {
            Column column = new Column();
            column.name = name;
            columns.add(column);
            return column;
        }

        public void resolve(RelationalProfile profile) {
            if (parentTable == null || parentTable.isEmpty()) return;
            for (Table table : profile.tables) {
                if (table.name.equals(parentTable)) {
                    parent = table;
                    break;
                }
            }
            if (parent == null) {
                throw new RuntimeException(String.format("Parent %s of %s not found", parentTable, name));
            }
            for (Column column : columns) {
                try {
                    column.resolve(parent);
                }
                catch (RuntimeException e) {
                    throw new RuntimeException(column + " didn't resolve in " + this, e);
                }
                if (column.link != null) {
                    if (linkColumn != null) {
                        throw new RuntimeException("Multiple link columns not permitted in " + name);
                    }
                    linkColumn = column;
                }
            }
            if (linkColumn == null) {
                throw new RuntimeException("No link column for table " + name);
            }
        }

        public String toQuery(String key) {
            if (query == null) {
                String qstring = String.format("select * from %s", name);
                if (key != null) qstring += String.format(" where %s='%s'", linkColumn.name, key);
                System.out.println(qstring);
                return qstring;
            }
            else {
                if (key != null) {
                    return String.format(query, key);
                }
                else {
                    return query;
                }
            }
        }

        public String toString() {
            return String.format("Table(%s)", name);
        }
    }

    @XStreamAlias("column")
    public static class Column {
        @XStreamAsAttribute
        public String name;

        @XStreamAsAttribute
        public ColumnType type;

        @XStreamAsAttribute
        public String linkColumn;

        @XStreamAsAttribute
        public Boolean key;

        @XStreamOmitField
        public Table parent;

        @XStreamOmitField
        public Column link;

        public void resolve(Table parentTable) {
            parent = parentTable;
            if (linkColumn == null) {
                if (key == null || !key) return;
                linkColumn = name;
            }
            for (Column column : parentTable.columns) {
                if (column.name.equals(linkColumn)) link = column;
            }
            if (link == null) {
                throw new RuntimeException(String.format("Link column %s not found in %s", linkColumn, parentTable.name));
            }
        }

        public boolean isEmpty(String value) {
            switch (type) {
                case INTEGER:
                    return "0".equals(value);
                case LONGVARCHAR:
                case LONGNVARCHAR:
                case VARCHAR:
                case NVARCHAR:
                    return value.isEmpty();
                case BINARY:
                    break;
                case LONGVARBINARY:
                    break;
                case TINYINT:
                    break;
                case BIT:
                    break;
                case CHAR:
                    break;
                case NUMERIC:
                    break;
                case DECIMAL:
                    break;
                case SMALLINT:
                    break;
                case DOUBLE:
                    break;
                case TIMESTAMP:
                    break;
            }
            return false;
        }

        public String toString() {
            return String.format("Column(%s)", name);
        }
    }

    public enum ColumnType {
        LONGVARCHAR(-1),
        LONGNVARCHAR(-16),
        BINARY(-2),
        LONGVARBINARY(-4),
        TINYINT(-6),
        BIT(-7),
        NVARCHAR(-9),
        CHAR(1),
        VARCHAR(12),
        NUMERIC(2),
        DECIMAL(3),
        INTEGER(4),
        SMALLINT(5),
        DOUBLE(8),
        DATE(91),
        TIMESTAMP(93);

        private final int typeInt;

        private ColumnType(int typeInt) {
            this.typeInt = typeInt;
        }

        public static ColumnType forTypeInt(int typeInt) {
            for (ColumnType columnType : values()) {
                if (columnType.typeInt == typeInt) return columnType;
            }
            throw new RuntimeException("No ColumnType for " + typeInt);
        }
    }

    public static RelationalProfile createProfile(Connection connection) throws SQLException {
        RelationalProfile profile = new RelationalProfile();
        DatabaseMetaData metaData = connection.getMetaData();
        String[] types = {"TABLE"};
        ResultSet tableResults = metaData.getTables(null, null, "%", types);
        while (tableResults.next()) {
            Table table = profile.addTable(tableResults.getString(3));
            Statement statement = connection.createStatement();
            ResultSet columnResults = statement.executeQuery(String.format("select * from %s where 1=0", table.name));
            ResultSetMetaData meta = columnResults.getMetaData();
            for (int col = 1; col < meta.getColumnCount(); col++) {
                Column column = table.addColumn(meta.getColumnName(col));
                column.type = ColumnType.forTypeInt(meta.getColumnType(col));
            }
        }
        return profile;
    }

    public static RelationalProfile createProfile(Connection connection, QueryDefinitions queryDefinitions) throws SQLException {
        RelationalProfile profile = new RelationalProfile();
        for (Query query : queryDefinitions.queries) {
            Table table = profile.addTable(query.name);
            table.parentTable = query.parentTable;
            table.query = query.content;
            Statement statement = connection.createStatement();
            ResultSet columnResults = statement.executeQuery(query.content);
            ResultSetMetaData meta = columnResults.getMetaData();
            for (int col = 1; col < meta.getColumnCount(); col++) {
                String columnName = meta.getColumnName(col);
                Column column = table.addColumn(columnName);
                column.type = ColumnType.forTypeInt(meta.getColumnType(col));
                if (columnName.equals(query.parentKey)) {
                    column.key = true;
                }
            }
        }
        return profile;
    }

    @XStreamAlias("query-definition")
    public static class QueryDefinitions {
        @XStreamImplicit
        List<Query> queries;
    }

    @XStreamAlias("query")
    @XStreamConverter(value = ToAttributedValueConverter.class, strings = {"content"})
    public static class Query {
        @XStreamAsAttribute
        public String name;

        @XStreamAsAttribute
        public String parentTable;

        @XStreamAsAttribute
        public String parentKey;

        public String content;
    }

    /*
        This gave no results:

            ResultSet importedKeys = metaData.getImportedKeys(null, null, table.name);
            ResultSetMetaData importedMeta = importedKeys.getMetaData();
            while (importedKeys.next()) {
                for (int col = 1; col < importedMeta.getColumnCount(); col++) {
                    System.out.println(String.format(
                            "%s=%s",
                            importedMeta.getColumnName(col),
                            importedKeys.getString(importedMeta.getColumnName(col))
                    ));
                }
            }
     */
}
