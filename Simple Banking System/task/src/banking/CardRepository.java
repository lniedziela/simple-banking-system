package banking;

import org.sqlite.SQLiteDataSource;

import java.sql.*;

public class CardRepository implements AutoCloseable {
    final Connection connection;

    public CardRepository(String fileName) throws SQLException {
        String url = "jdbc:sqlite:" + fileName;
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(url);
        connection = dataSource.getConnection();
        createTable();
    }

    @Override
    public void close() throws Exception {
        try (connection) {
        }
    }

    public CardEntity createCard(CardEntity cardEntity) {
        String insert = "INSERT INTO CARD(number, pin, balance) VALUES (?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            String number = cardEntity.number;
            String pin = cardEntity.pin;
            int balance = cardEntity.balance;

            statement.setString(1, number);
            statement.setString(2, pin);
            statement.setInt(3, balance);
            statement.execute();
            return new CardEntity(number, pin, balance);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public CardEntity findByCardNumber(String enteredNumber) {
        String select = "SELECT number, pin, balance FROM card WHERE number=?";
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, enteredNumber);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                String number = resultSet.getString("number");
                String pin = resultSet.getString("pin");
                int balance = resultSet.getInt("balance");
                return new CardEntity(number, pin, balance);
            } else {
                throw new CardNotFoundException();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateBalanceByNumber(int enteredBalance, String enteredNumber) {
        String update = "UPDATE card SET balance=balance+? WHERE number=?";

        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setInt(1, enteredBalance);
            statement.setString(2, enteredNumber);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getBalanceByNumber(String enteredNumber) {
        String select = "SELECT balance FROM card WHERE number=?";

        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, enteredNumber);
            int balance;
            try (ResultSet resultSet = statement.executeQuery()) {
                balance = resultSet.getInt("balance");
            }
            return balance;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasCardWithNumber(String enteredNumber) {
        String select = "SELECT 1 FROM card WHERE number=?";
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, enteredNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void transfer(String numberFrom, String numberTo, int money) {
        try {
            connection.setAutoCommit(false);
            String update1 = "UPDATE card\n" +
                    "SET balance = balance - ?\n" +
                    "WHERE number = ? AND balance >= ?;";
            try (PreparedStatement statement = connection.prepareStatement(update1)) {
                statement.setInt(1, money);
                statement.setString(2, numberFrom);
                statement.setInt(3, money);
                int response = statement.executeUpdate();
                if (response != 1) {
                    connection.rollback();
                    throw new NotEnoughMoneyException();
                }
            }
            String update2 = "UPDATE card\n" +
                    "SET balance = balance + ?\n" +
                    "WHERE number = ?;";
            try (PreparedStatement statement = connection.prepareStatement(update2)) {
                statement.setInt(1, money);
                statement.setString(2, numberTo);
                int response = statement.executeUpdate();
                if (response == 1) {
                    connection.commit();
                } else {
                    connection.rollback();
                    throw new IllegalArgumentException("Problem during transferring money.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteByNumber(String number) {
        String delete = "DELETE FROM card WHERE number=?";
        try (PreparedStatement statement = connection.prepareStatement(delete)) {
            statement.setString(1, number);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void createTable() {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS card(" +
                    "id INTEGER PRIMARY KEY, GENERATED VALUE," +
                    "number VARCHAR(16) NOT NULL," +
                    "pin VARCHAR(4) NOT NULL," +
                    "balance INTEGER)");

            try (ResultSet cards = statement.executeQuery("SELECT * FROM card")) {
                while (cards.next()) {
                    int id = cards.getInt("id");
                    String number = cards.getString("number");
                    String pin = cards.getString("pin");
                    int balance = cards.getInt("balance");
                    System.out.printf("Id %d%n", id);
                    System.out.printf("\tNumber: %s%n", number);
                    System.out.printf("\tPIN: %s%n", pin);
                    System.out.printf("\tBalance: %d%n", balance);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
