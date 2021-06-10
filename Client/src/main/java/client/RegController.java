package client;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegController {
    @FXML
    public PasswordField passwordField;
    @FXML
    public TextField loginField;
    @FXML
    public Button reg;
    @FXML
    public Button auth;

    private Controller controller;

    public void setController(Controller controller) {
        this.controller = controller;
    }

    public void registration(ActionEvent actionEvent) {
        String login = loginField.getText().trim();
        String password = passwordField.getText().trim();
        if (login.length() * password.length() == 0) {
            return;
        }
        controller.registration(login, password);
        loginField.clear();
        passwordField.clear();
    }

    public void authentication(ActionEvent actionEvent) {
        String login = loginField.getText().trim();
        String password = passwordField.getText().trim();
        if (login.length() * password.length() == 0) {
            return;
        }
        controller.authentication(login, password);
        loginField.clear();
        passwordField.clear();
    }
}
