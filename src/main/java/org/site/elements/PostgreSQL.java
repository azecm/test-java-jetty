package org.site.elements;

import org.jetbrains.annotations.Nullable;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.site.view.VUtil;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class PostgreSQL {

    public class Common {
        protected String _table;
        protected int _setCounter = 0;
        protected ArrayList<String> _setK = new ArrayList<>();
        //protected ArrayList<String> _setV = new ArrayList<>();
        protected HashMap<Integer, String> _setPString = new HashMap<>();
        protected HashMap<Integer, Integer> _setPInteger = new HashMap<>();
        protected HashMap<Integer, Boolean> _setPBoolean = new HashMap<>();


        protected void _setKey(String column) {
            String k = key(column);
            if (!_setK.contains(k)) _setK.add(k);
        }

        protected void _set(String column, String value) {
            _setKey(column);
            _setPString.put(++_setCounter, value);
        }

        protected void _set(String column, int value) {
            _setKey(column);
            _setPInteger.put(++_setCounter, value);
        }

        protected void _set(String column, boolean value) {
            _setKey(column);
            _setPBoolean.put(++_setCounter, value);
        }

        @SuppressWarnings("Duplicates")
        protected void exexSql(String sql) {
            if (flagViewSql) System.out.println(sql);

            try {
                if (connection != null) {
                    PreparedStatement pstmt = connection.prepareStatement(sql);

                    for (Map.Entry<Integer, String> p : _setPString.entrySet()) {
                        pstmt.setString(p.getKey(), p.getValue());
                        if (flagViewSql) VUtil.println(p.getKey(), p.getValue());
                    }
                    for (Map.Entry<Integer, Integer> p : _setPInteger.entrySet()) {
                        pstmt.setInt(p.getKey(), p.getValue());
                        if (flagViewSql) VUtil.println(p.getKey(), p.getValue());
                    }
                    for (Map.Entry<Integer, Boolean> p : _setPBoolean.entrySet()) {
                        pstmt.setBoolean(p.getKey(), p.getValue());
                        if (flagViewSql) VUtil.println(p.getKey(), p.getValue());
                    }

                    pstmt.executeUpdate();

                    pstmt.close();
                }
            } catch (SQLException e) {
                error("PostgreSQL statementInsert Failed", e);
            }

        }
    }

    public class Insert extends Common {
        private ArrayList<String> _values = new ArrayList<>();
        private ArrayList<String> _line = new ArrayList<>();

        Insert(String table) {
            _table = table;
        }

        public Insert cols(String... cols) {
            _setK.addAll(Arrays.asList(cols).stream().map(k -> key(k)).collect(Collectors.toList()));
            return this;
        }

        public Insert values() {
            if (_line.size() > 0) {
                _values.add("(" + String.join(",", _line) + ")");
            }
            _line = new ArrayList<>();
            return this;
        }

        public Insert set(String value) {
            _line.add("?");
            _setPString.put(++_setCounter, value);
            return this;
        }

        public Insert set(int value) {
            _line.add("?");
            _setPInteger.put(++_setCounter, value);
            return this;
        }

        public Insert set(boolean value) {
            _line.add("?");
            _setPBoolean.put(++_setCounter, value);
            return this;
        }

        public void exec() {
            values();
            String sql = "INSERT INTO " + schemaDot + _table;
            sql += " (" + String.join(", ", _setK) + ")";
            //sql += " VALUES (" + + ")";
            sql += " VALUES " + String.join(", ", _values);
            sql += ";";
            exexSql(sql);
        }

    }

    public class Delete extends Common {
        private ArrayList<String> _where = new ArrayList<>();

        Delete(String table) {
            _table = table;
        }

        public Delete where(String column, int value) {
            _where.add(key(column) + "=" + value);
            return this;
        }

        public void exec() {
            String sql = "DELETE FROM " + schemaDot + _table;
            sql += " WHERE " + String.join(" AND ", _where);
            sql += ";";
            exexSql(sql);
        }
    }

    public class Update extends Common {
        private ArrayList<String> _where = new ArrayList<>();
        private ArrayList<String> _setV = new ArrayList<>();

        Update(String table) {
            _table = table;
        }

        public Update set(String column, String value) {
            _set(column, value);
            _setV.add("?");
            return this;
        }

        public Update set(String column, int value) {
            _set(column, value);
            _setV.add("?");
            return this;
        }

        public Update set(String column, boolean value) {
            _set(column, value);
            _setV.add("?");
            return this;
        }

        public Update where(String column, int value) {
            _where.add(key(column) + "=" + value);
            return this;
        }

        //public Update where(String column, String value) {
        //    _where.add(key(column) + "= ?");
        //    _setPString.put(++_setCounter, value);
        //    return this;
        //}

        public void exec() {
            String sql = "UPDATE " + schemaDot + _table;
            sql += " SET (" + String.join(", ", _setK) + ")";
            sql += " = (" + String.join(", ", _setV) + ")";
            sql += " WHERE " + String.join(" AND ", _where);
            sql += ";";

            exexSql(sql);
        }
    }

    public class Create extends Common {
        ArrayList<String> colData;
        ArrayList<String> sqlData = new ArrayList<>();

        public Create(String table) {
            _table = table;
        }

        public void exec() {
            newLine();

            String cols = String.join(", ", sqlData);

            String sql = "CREATE TABLE " + schemaDot + _table + " (" + cols + ");";

            exexSql(sql);
        }

        void newLine() {
            if (colData != null) {
                sqlData.add(String.join(" ", colData));
            }
            colData = new ArrayList<>();
        }

        public Create col(String column) {
            newLine();
            colData.add(key(column));
            return this;
        }

        public Create primaryKey() {
            colData.add("PRIMARY KEY");
            return this;
        }

        public Create unique() {
            colData.add("UNIQUE");
            return this;
        }

        public Create notNull() {
            colData.add("NOT NULL");
            return this;
        }

        public Create defaultVal(String val) {
            colData.add("DEFAULT '" + val + "'");
            return this;
        }

        public Create defaultVal(int val) {
            colData.add("DEFAULT " + val);
            return this;
        }

        public Create defaultVal(boolean val) {
            colData.add("DEFAULT " + val);
            return this;
        }

        public Create timestamp() {
            //grant_date timestamp without time zone
            colData.add("TIMESTAMP");
            return this;
        }

        public Create serial() {
            colData.add("serial");
            return this;
        }

        public Create bigserial() {
            colData.add("bigserial");
            return this;
        }

        public Create integer() {
            colData.add("integer");
            return this;
        }

        public Create bigint() {
            colData.add("bigint");
            return this;
        }

        public Create bool() {
            colData.add("boolean");
            return this;
        }

        public Create varchar(int length) {
            colData.add("varchar(" + length + ")");
            return this;
        }

        //.col("idn").integer().primaryKey().notNull();
    }

    public class Select<T> {
        private String _table;
        @Nullable
        private String _where;
        @Nullable
        private String _order;
        private ArrayList<String> _groupBy;
        private int _limit = 0;
        private int _offset = 0;
        private Class<T> classRef;
        private HashSet<String> _sum = new HashSet<>();

        private HashMap<String, String> fields = null;

        protected int _setCounter = 0;
        //protected ArrayList<String> _setK = new ArrayList<>();
        //protected ArrayList<String> _setV = new ArrayList<>();
        protected HashMap<Integer, String> _setPString = new HashMap<>();
        protected HashMap<Integer, Integer> _setPInteger = new HashMap<>();
        protected HashMap<Integer, Boolean> _setPBoolean = new HashMap<>();


        Select(Class<T> classRef) {
            setFields(classRef);
            this.classRef = classRef;
        }

        private void setFields(Class classRef) {
            fields = new HashMap<>();
            for (Field field : classRef.getDeclaredFields()) {
                int m = field.getModifiers();
                if (!Modifier.isPrivate(m) && !Modifier.isStatic(m) && !Modifier.isFinal(m)) {
                    fields.put(field.getName(), field.getType().getName());
                    //if (name == 'count') name = 'COUNT(*)';
                }
            }
        }

        public Select<T> from(String table) {
            _table = table;
            return this;
        }

        public Select<T> fromTree() {
            _table = "tree";
            return this;
        }

        public Select<T> where(String where) {
            _where = where;
            return this;
        }

        public Select<T> or(String column, String expression) {
            if (_where == null) _where = "";
            _where += (_where.isEmpty() ? "" : " OR ") + key(column) + expression;
            return this;
        }

        private Select<T> _and(String column, String expression) {
            if (_where == null) _where = "";
            _where += (_where.isEmpty() ? "" : " AND ") + key(column) + expression;
            return this;
        }

        public Select<T> and(String column, String expression) {
            return _and(column, " = '" + expression + "'");
        }

        public Select<T> and(String column, boolean expression) {
            return _and(column, " is " + expression);
        }

        public Select<T> and(String column, int expression) {
            return _and(column, "=" + expression);
        }

        public Select<T> andIn(String column, String expression) {
            return _and(column, " in (" + expression + ")");
        }

        public Select<T> andIn(String column, Set<String> expression) {
            ArrayList<String> c = new ArrayList<>();
            for (String value : expression) {
                c.add("?");
                _setPString.put(++_setCounter, value);
            }
            return _and(column, " in (" + String.join(",", c) + ")");
        }

        public Select<T> andMore(String column, int expression) {
            return _and(column, ">" + expression);
        }

        public Select<T> andLess(String column, int expression) {
            return _and(column, "<" + expression);
        }

        public Select<T> andEqual(String column, int expression) {
            return _and(column, "=" + expression);
        }

        public Select<T> order(String column, boolean flagAsc) {
            // https://www.postgresql.org/docs/9.4/static/queries-order.html
            // [ORDER BY column1, column2, .. columnN] [ASC | DESC]
            if (_order == null) _order = "";
            _order += (_order.isEmpty() ? "" : ", ") + key(column) + " " + (flagAsc ? "ASC" : "DESC");
            return this;
        }

        public Select<T> limit(int limit) {
            _limit = limit;
            return this;
        }

        public Select<T> offset(int offset) {
            _offset = offset;
            return this;
        }

        public Select<T> groupBy(String column) {
            if (_groupBy == null) _groupBy = new ArrayList<>();
            _groupBy.add(key(column));
            return this;
        }

        public Select<T> sum(String column) {
            //_groupBy = key(column);
            _sum.add(column);
            return this;
        }

        @SuppressWarnings("Duplicates")
        ArrayList<T> query(String slqText, Class<T> classRef) {
            if (flagViewSql) System.out.println(slqText);

            ArrayList<T> list = new ArrayList<>();

            if (connection != null) {
                try {
                    PreparedStatement pstmt = connection.prepareStatement(slqText);

                    for (Map.Entry<Integer, String> p : _setPString.entrySet()) {
                        pstmt.setString(p.getKey(), p.getValue());
                        if (flagViewSql) VUtil.println(p.getKey(), p.getValue());
                    }
                    //for (Map.Entry<Integer, Integer> p : _setPInteger.entrySet())
                    //    pstmt.setInt(p.getKey(), p.getValue());
                    //for (Map.Entry<Integer, Boolean> p : _setPBoolean.entrySet())
                    //    pstmt.setBoolean(p.getKey(), p.getValue());

                    ResultSet rs = pstmt.executeQuery();


                    while (rs.next()) {
                        T elem = newLine(classRef);
                        for (HashMap.Entry<String, String> field : fields.entrySet()) {
                            String key = field.getKey();
                            try {
                                Field f2 = elem.getClass().getField(key);
                                switch (field.getValue()) {
                                    case "int":
                                        f2.setInt(elem, rs.getInt(key));
                                        break;
                                    case "boolean":
                                        f2.setBoolean(elem, rs.getBoolean(key));
                                        break;
                                    case "java.lang.String":
                                        f2.set(elem, rs.getString(key));
                                        break;
                                    default:
                                        System.out.println(key + "::type::" + field.getValue());
                                        break;
                                }
                            } catch (NoSuchFieldException | IllegalAccessException e) {
                                error("PostgreSQL query Failed", e);
                            }
                        }
                        list.add(elem);
                    }
                    rs.close();
                    pstmt.close();
                } catch (SQLException | NullPointerException e) {
                    error("PostgreSQL query Failed", e);
                }
            } else {
                errorText("PostgreSQL connection==null");
            }
            //statementClose();

            return list;
        }


        //@Nullable
        public ArrayList<T> exec() {
            String sql = "SELECT";
            if (fields == null) {
                sql += " *";
            } else {
                ArrayList<String> keys = new ArrayList<>();
                for (String key : fields.keySet()) {
                    if (_sum.contains(key)) {
                        keys.add("SUM(" + key(key) + ") as " + key(key));
                    } else {
                        keys.add(key(key));
                    }
                }

                sql += " " + String.join(", ", keys);
            }
            sql += " FROM " + schemaDot + _table;


            if (_where != null) {
                sql += " WHERE " + _where;
            }

            if (_groupBy != null) {
                sql += " GROUP BY " + String.join(",", _groupBy);
            }

            if (_order != null) {
                sql += " ORDER BY " + _order;
            }

            if (_limit > 0) {
                sql += " LIMIT " + _limit;
            }
            if (_offset > 0) {
                sql += " OFFSET " + _offset;
            }

            return query(sql, classRef);
        }
    }

    static final String DB_URL = "jdbc:postgresql://localhost:5432/form";
    static final String USER = "former";
    static final String PASS = "yeqer6";
    final Pattern reCase = Pattern.compile("[A-Z]");

    //public enum Day {
    //    SUNDAY, MONDAY, TUESDAY, WEDNESDAY,
    //    THURSDAY, FRIDAY, SATURDAY
    //}

    @Nullable
    private Connection connection = null;
    @Nullable
    private Statement stmt = null;
    //private PreparedStatement pstmt = null;

    private String schema = "";
    private String schemaDot = "";

    private Boolean flagViewSql = false;


    public PostgreSQL() {
    }

    public PostgreSQL(String host) {
        schema = VUtil.getHostKey(host);
        schemaDot = schema + ".";
    }

    public PostgreSQL connect() {
        try {
            connection = DriverManager.getConnection(DB_URL, USER, PASS);
        } catch (SQLException e) {
            error("PostgreSQL Connection Failed", e);
        }
        return this;
    }

    private String key(String name) {
        return reCase.matcher(name).find() ? (name.equals("COUNT") ? "COUNT(*)" : "\"" + name + "\"") : name;
    }

    void statementOpen() {

        try {
            if (connection != null) {
                stmt = connection.createStatement();
                //stmt = connection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
            } else {
                errorText("PostgreSQL not connected (statementOpen).");
            }
        } catch (SQLException e) {
            error("PostgreSQL statementOpen Failed", e);
        }
    }

    public void statementClose() {
        try {
            if (stmt != null) stmt.close();
        } catch (SQLException e) {
            error("PostgreSQL statementClose Failed", e);
        }
    }

    void error(String text, Exception e) {
        System.out.println(text);
        e.printStackTrace();
    }

    private void errorText(String text) {
        System.out.println(text);
    }

    public void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException e) {
            error("PostgreSQL Connection close Failed", e);
        }
    }

    public PostgreSQL viewSql() {
        flagViewSql = true;
        return this;
    }

    private <T> T newLine(Class<T> classRef) {
        T line = null;
        try {
            line = classRef.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            error("newLine", e);
        }
        return line;
    }


    @Nullable
    public ResultSet query(String slqText) {
        ResultSet rs = null;
        if (stmt != null) {
            try {
                if (!slqText.endsWith(";")) slqText += ";";
                if (flagViewSql) VUtil.println(slqText);
                rs = stmt.executeQuery(slqText);
            } catch (SQLException e) {
                error("PostgreSQL query Failed: " + slqText, e);
            }
        } else {
            errorText("PostgreSQL statement==null");
        }
        return rs;
    }

    public boolean queryCmd(String slqText) {
        boolean res = false;
        statementOpen();
        if (stmt != null) {
            try {
                if (!slqText.endsWith(";")) slqText += ";";
                if (flagViewSql) VUtil.println(slqText);
                res = stmt.execute(slqText);
            } catch (SQLException e) {
                error("PostgreSQL query Failed: " + slqText, e);
            }
        } else {
            errorText("PostgreSQL statement==null");
        }
        statementClose();
        return res;
    }


    public <T> Select<T> select(Class<T> classRef) {
        return new Select<>(classRef);
    }

    public void dropTable(String tableName) {
        String sql = "DROP TABLE IF EXISTS " + schemaDot + tableName + " CASCADE;";
        queryCmd(sql);
    }

    public Create createTable(String tableName) {
        return new Create(tableName);
    }

    public Update update(String tableName) {
        return new Update(tableName);
    }

    public Insert insert(String tableName) {
        return new Insert(tableName);
    }

    //@SuppressWarnings("Duplicates")
    public void copyfromCSV(String tableName, String fileName, String... columns) {
        final CopyManager cm;
        InputStream outputStream;
        if (!fileName.endsWith(".csv")) fileName += ".csv";
        File file = new File(fileName);

        try {
            outputStream = new FileInputStream(file);
            cm = new CopyManager((BaseConnection) connection);
            String sql = "COPY " + this.schemaDot + tableName + (columns.length > 0 ? "(" + String.join(",", Arrays.stream(columns).map(a -> key(a)).collect(Collectors.toList())) + ")" : "") + " FROM STDIN WITH CSV HEADER DELIMITER ';';";
            if (flagViewSql) VUtil.println(sql);
            cm.copyIn(sql, outputStream);
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    //@SuppressWarnings("Duplicates")
    public void copyToCSV(String tableName, String fileName) {
        final CopyManager cm;
        OutputStream outputStream;
        if (!fileName.endsWith(".csv")) fileName += ".csv";
        File file = new File(fileName);

        try {
            outputStream = new FileOutputStream(file);
            cm = new CopyManager((BaseConnection) connection);
            String sql = "COPY " + this.schemaDot + tableName + " TO STDOUT WITH CSV HEADER DELIMITER ';';";
            if (flagViewSql) VUtil.println(sql);
            cm.copyOut(sql, outputStream);
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }

    }

    public Delete delete(String tableName) {
        // let list = ['DELETE', 'FROM', this.schemaDot + table, 'WHERE', getWhere(data, where)];
        return new Delete(tableName);
    }

    public boolean begin() {
        return queryCmd("BEGIN;");
    }

    public boolean commit() {
        return queryCmd("COMMIT;");
    }

    //db.delete(tableName)

    public Map<Integer, NodeTreeElem> getTree() {
        Map<Integer, NodeTreeElem> map = new HashMap<>();
        String sql = "SELECT idn,idp,idu,text,path,prev,next,first,last FROM " + schemaDot + "tree;";
        statementOpen();
        ResultSet rs = query(sql);
        if (rs != null) {
            try {
                while (rs.next()) {
                    NodeTreeElem elem = new NodeTreeElem(rs.getInt("idn"))
                            .idp(rs.getInt("idp"))
                            .idu(rs.getInt("idu"));


                    elem.prev = rs.getInt("prev");
                    elem.next = rs.getInt("next");
                    elem.first = rs.getInt("first");
                    elem.last = rs.getInt("last");

                    elem.text = rs.getString("text");
                    elem.path = rs.getString("path");

                    map.put(elem.idn, elem);
                }
                rs.close();
            } catch (SQLException e) {
                error("PostgreSQL getTree Failed", e);
            }
        }
        statementClose();
        return map;
    }
}
