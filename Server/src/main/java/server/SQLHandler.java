package server;

import java.sql.*;

public class SQLHandler {
    private static Connection connection;
    private static PreparedStatement psChangeNick;
    private static PreparedStatement psRegistration;
    private static PreparedStatement psGetNickname;

    public static boolean connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:server/UsersDB.db");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void disconnect() {
        try {
            connection.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    public static String getNicknameAndPassword(String login, String password) {
        String nick = null;
        try {
            psGetNickname = connection.prepareStatement("select login from users WHERE login = ? AND password = ?;");
            psGetNickname.setString(1, login);
            psGetNickname.setString(2, password);
            ResultSet rs = psGetNickname.executeQuery();
            if (rs.next()) {
                nick = rs.getString(1);
            }
            rs.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return nick;
    }

    public static boolean registration(String login, String password) {
        try {
            psGetNickname = connection.prepareStatement("select login FROM users WHERE login = ? AND password = ?;");
            psGetNickname.setString(1, login);
            psGetNickname.setString(2, password);
            ResultSet rs = psGetNickname.executeQuery();
            if (rs.next()) {
                rs.close();
                return false;
            } else {
                rs.close();
                psRegistration = connection.prepareStatement("INSERT INTO users (login, password) VALUES (?, ?)");
                psRegistration.setString(1, login);
                psRegistration.setString(2, password);
                psRegistration.executeUpdate();
                return true;
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        }
    }
}
