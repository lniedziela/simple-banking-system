package banking;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        try (var cardRepository = new CardRepository(
                getParameter("-fileName", args).orElse("card.s3db")
        )) {
            new Bank(cardRepository).run();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static Optional<String> getParameter(String name, String[] args) {
        return Arrays.stream(args)
                .dropWhile(s -> !Objects.equals(name, s))
                .skip(1)
                .findFirst();
    }
}

class CardEntity {
    String number;
    String pin;
    int balance;

    public CardEntity(String number, String pin, int balance) {
        this.number = number;
        this.pin = pin;
        this.balance = balance;
    }

    public String getNumber() {
        return number;
    }

    public String getPin() {
        return pin;
    }

    public int getBalance() {
        return balance;
    }
}

class CardNotFoundException extends RuntimeException {
}

class NotEnoughMoneyException extends RuntimeException {
}

class Bank {
    private static final Scanner SCANNER = new Scanner(System.in);

    private static CardRepository cardRepository;

    public Bank(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public void run() {
        State state = new Start();

        while (state != State.TERMINAL) {
            state = state.doWork();
        }
    }

    private interface State {
        State TERMINAL = new State() {
        };

        default State doWork() {
            return this;
        }
    }

    private static class MapperEntityCard2Card {
        public Card map(CardEntity cardEntity) {
            String number = cardEntity.getNumber();
            String pin = cardEntity.getPin();
            int balance = cardEntity.getBalance();

            return new Card(number, pin, balance);
        }
    }

    private static class Card {
        private static final Random RANDOM = new Random();

        private final String number;
        private final String pin;
        private int balance;

        public Card(String number, String pin, int balance) {
            this.number = number;
            this.pin = pin;
            this.balance = balance;
        }

        private Card(String number, String pin) {
            this.number = number;
            this.pin = pin;
            this.balance = 0;
        }

        private Card(String number) {
            this(number, generateCardPin());
        }

        private Card() {
            this(generateCardNumber());
        }

        public int getBalance() {
            return balance;
        }

        private static String generateCardNumber() {
            var cardNumber = new StringBuilder("400000");

            int generatedCardNumberLength = 9;
            for (int i = 0; i < generatedCardNumberLength; i++) {
                int digit = RANDOM.nextInt(10);
                cardNumber.append(digit);
            }
            var result = cardNumber.toString();
            return result + calculateLuhnCheckDigit(result);
        }

        private static String calculateLuhnCheckDigit(String number) {
            int sum = 0;
            for (int i = 0; i < number.length(); i++) {
                int digit = number.charAt(i) - '0';
                if (i % 2 == 0) {
                    digit *= 2;
                    if (digit > 9) {
                        digit -= 9;
                    }
                }
                sum += digit;
            }
            int checkDigit = (1000 - sum) % 10;
            return String.valueOf(checkDigit);
        }

        private static boolean checkLuhn(String number) {
            return number.length() == 16 &&
                    Objects.equals(
                            calculateLuhnCheckDigit(number.substring(0, 15)),
                            number.substring(15)
                    );
        }

        private static String generateCardPin() {
            return String.format("%04d", RANDOM.nextInt(10_000));
        }

        public static Card createNew() {
            var card = new Card();
            var cardEntity = cardRepository.createCard(
                    new CardEntity(card.number, card.pin, card.getBalance())
            );
            return new MapperEntityCard2Card().map(cardEntity);
        }

        public static boolean isValid(String enteredNumber, String enteredPin) {
            CardEntity byCardNumber;
            try {
                byCardNumber = cardRepository.findByCardNumber(enteredNumber);
            } catch (CardNotFoundException e) {
                return false;
            }
            var card = new MapperEntityCard2Card().map(byCardNumber);
            String pin = card.pin;
            return Objects.equals(pin, enteredPin);
        }

        @Override
        public String toString() {
            return "Your card number:\n" +
                    number +
                    "\nYour card PIN:\n" +
                    pin;
        }
    }

    private class Start implements State {
        @Override
        public State doWork() {
            System.out.println("\n1. Create an account\n" +
                    "2. Log into account\n" +
                    "0. Exit");
            String chosenAction = SCANNER.nextLine();
            switch (chosenAction) {
                case "1":
                    return new CreateAccount();
                case "2":
                    return new Login();
                case "0":
                    return new Exit();
                default:
                    return this;
            }
        }
    }

    private class Exit implements State {
        @Override
        public State doWork() {
            System.out.println("\nBye!");
            return State.TERMINAL;
        }
    }

    private class CreateAccount implements State {
        @Override
        public State doWork() {
            var card = Card.createNew();
            System.out.println("\nYour card has been created");
            System.out.println(card);
            return new Start();
        }
    }

    private class Login implements State {
        @Override
        public State doWork() {
            System.out.println("\nEnter your card number:");
            String enteredNumber = SCANNER.nextLine();
            System.out.println("Enter your PIN:");
            String enteredPin = SCANNER.nextLine();

            if (Card.isValid(enteredNumber, enteredPin)) {
                System.out.println("\nYou have successfully logged in!");
                return new LoggedAs(enteredNumber);
            } else {
                System.out.println("\nWrong card number or PIN!");
                return new Start();
            }
        }
    }

    private class LoggedAs implements State {
        private final String number;

        public LoggedAs(String number) {
            this.number = number;
        }

        @Override
        public State doWork() {
            System.out.printf("%n1. Balance%n" +
                    "2. Add income%n" +
                    "3. Do transfer%n" +
                    "4. Close account%n" +
                    "5. Log out%n" +
                    "0. Exit%n");
            String chosenAction = SCANNER.nextLine();
            switch (chosenAction) {
                case "1":
                    return new Balance(number);
                case "2":
                    return new AddIncome(number);
                case "3":
                    return new Transfer(number);
                case "4":
                    return new CloseAccount(number);
                case "5":
                    return new LoggedOut();
                case "0":
                    return new Exit();
                default:
                    return this;
            }
        }
    }

    private class Balance implements State {
        private final String number;

        public Balance(String number) {
            this.number = number;
        }

        @Override
        public State doWork() {
            int balance = cardRepository.getBalanceByNumber(number);
            System.out.printf("%nBalance: %d%n", balance);
            return new LoggedAs(number);
        }
    }

    private class AddIncome implements State {
        private final String number;

        public AddIncome(String number) {
            this.number = number;
        }

        @Override
        public State doWork() {
            System.out.println("Enter income:");
            int income = SCANNER.nextInt();
            SCANNER.nextLine();
            if (income > 0) {
                cardRepository.updateBalanceByNumber(income, number);
                System.out.println("Income was added!");
            } else {
                System.out.println("Income cannot be negative!");
            }
            return new LoggedAs(number);
        }
    }

    private class Transfer implements State {
        private final String number;

        public Transfer(String number) {
            this.number = number;
        }

        @Override
        public State doWork() {
            System.out.println("Transfer\n" +
                    "Enter card number:");
            String enteredCardNumber = SCANNER.nextLine();

            if (!Card.checkLuhn(enteredCardNumber)) {
                System.out.println("Probably you made a mistake in the card number. Please try again!");
                return new LoggedAs(number);
            } else if (!cardRepository.hasCardWithNumber(enteredCardNumber)) {
                System.out.println("Such a card does not exist.");
                return new LoggedAs(number);
            } else if (Objects.equals(number, enteredCardNumber)) {
                System.out.println("You can't transfer money to the same account!");
                return new LoggedAs(number);
            } else {
                System.out.println("Enter how much money you want to transfer:");
                int moneyToTransfer = SCANNER.nextInt();
                SCANNER.nextLine();
                if (moneyToTransfer > 0) {
                    try {
                        cardRepository.transfer(number, enteredCardNumber, moneyToTransfer);
                        System.out.println("Success!");
                    } catch (NotEnoughMoneyException e){
                        System.out.println("Not enough money!");
                    }
                }
            }
            return new LoggedAs(number);
        }
    }

    private class CloseAccount implements State {
        private final String number;

        public CloseAccount(String number) {
            this.number = number;
        }

        @Override
        public State doWork() {
            cardRepository.deleteByNumber(number);
            return new Start();
        }
    }

    private class LoggedOut implements State {
        @Override
        public State doWork() {
            System.out.println("\nYou have successfully logged out!");
            return new Start();
        }
    }
}
