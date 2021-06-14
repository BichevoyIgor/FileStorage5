package client;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.ResourceBundle;

public class Controller implements Initializable {

    private Socket socket;
    private Stage stage;
    private final int PORT = 9997;
    private DataInputStream in;
    private DataOutputStream out;
    private final String HOST = "localhost";
    private String currentPath;
    private String serverPath;
    private String pathForWindow;

    private RegController regController;
    private Stage regStage;

    @FXML
    public VBox serverPart;
    @FXML
    public TextField clientTextField;
    @FXML
    public TextField serverTextField;
    @FXML
    public ListView<String> clientFileList;
    @FXML
    public ListView<String> serverFileList;
    @FXML
    public ComboBox diskBox;
    @FXML
    public MenuItem auth;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        connect();
        //закрытие окна по крестику
        Platform.runLater(() -> {
            stage = (Stage) clientTextField.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                stage.close();
                disconnect();
            });
        });
        diskBoxChange();
        loadTableList();
        clientDoubleClick();
        serverDoubleClick();
    }

    //открытие папки при двойном клике мыши в серверной части
    private void serverDoubleClick() {
        serverFileList.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 2) {
                    String fileName = serverFileList.getSelectionModel().getSelectedItem();
                    try {
                        out.writeUTF("COMMAND_DIR_OR_FILE " + fileName);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (mouseEvent.getClickCount() == 1) {
                    clientFileList.getSelectionModel().clearSelection();
                }
            }
        });
    }

    //открытие папки при двойном клике мыши в клиентской части
    private void clientDoubleClick() {
        clientFileList.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 2) {
                    ObservableList<String> f = clientFileList.getSelectionModel().getSelectedItems();
                    String path = String.format("%s%s", currentPath, f.get(0).substring(0, f.get(0).length()));
                    if (Files.isDirectory(Path.of(path))) {
                        clientTextField.setText(path + "\\");
                        currentPath = clientTextField.getText();
                        loadTableList();
                    } else {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Для дальнейшего перехода просьба выбрать папку", ButtonType.OK);
                        alert.setHeaderText(null);
                        alert.showAndWait();
                    }
                }
                if (mouseEvent.getClickCount() == 1) {
                    serverFileList.getSelectionModel().clearSelection();
                }
            }
        });
    }

    //нить отвечающая за входящие команды, запускается при коннекте
    private void commandThreadStart() {
        new Thread(() -> {

            while (!socket.isClosed()) {
                //выводим окно авторизации
                Platform.runLater(() -> {
                    if (!serverPart.isVisible()) {
                        if (regStage == null) {
                            initRegWindow();
                            regStage.show();
                        }
                    }
                });
                try {
                    String command = in.readUTF();
                    if (command.startsWith("COMMAND_GETTREE_FROM_SERVER")) { // пришла строка вида "COMMAND_GETTREE_FROM_SERVER?папка?папка?файл"
                        String[] treeServer = command.split("\\?"); // делим ее на масив по разделителю ?
                        serverPath = treeServer[1];
                        changePath(serverPath);
                        Platform.runLater(() -> { //заполняем таблицу на форме
                            serverFileList.getItems().clear();
                            for (int i = 2; i < treeServer.length; i++) {
                                serverFileList.getItems().add(treeServer[i]);
                            }
                        });
                    } else if (command.startsWith("COMMAND_DIR_OR_FILE")) { //проверка на директорию или файл на стороне сервера
                        String[] answer = command.split(" ", 3);
                        if ("dir".equals(answer[1])) {
                            getFolderTreeFromServer(answer[2]);
                        } else if (answer[1].equals("file")) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Для дальнейшего перехода просьба выбрать папку", ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                            });
                        }
                    } else if (command.startsWith("COMMAND_UPLOAD_STATUS")) {
                        String[] answer = command.split(" ", 2);
                        if (answer[1].equals("ERROR")) {
                            String error = in.readUTF();
                            Platform.runLater(() -> {
                                loadTableList();
                                Alert alert = new Alert(Alert.AlertType.INFORMATION, error, ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                            });
                        } else {
                            Platform.runLater(() -> {
                                loadTableList();
                                Alert alert = new Alert(Alert.AlertType.INFORMATION, String.format("Отправка файла: %s, выполнена успешно", answer[1]), ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                            });
                        }
                    } else if (command.startsWith("COMMAND_DELETE_STATUS")) {
                        String[] answer = command.split(" ", 2);
                        Platform.runLater(() -> {
                            getFolderTreeFromServer(serverPath);
                            Alert alert = new Alert(Alert.AlertType.INFORMATION, answer[1], ButtonType.OK);
                            alert.setHeaderText(null);
                            alert.showAndWait();
                            loadTableList();
                        });
                    } else if (command.startsWith("COMMAND_NEW_FOLDER_STATUS ")) {
                        String[] answer = command.split(" ", 2);
                        if (answer[1].equals("ERROR")) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR, "Папка с таким имененем уже существует", ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                            });
                        } else {
                            Platform.runLater(() -> {
                                getFolderTreeFromServer(serverPath);
                                Alert alert = new Alert(Alert.AlertType.INFORMATION, String.format("Папка %S создана", answer[1]), ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                            });
                        }
                    } else if (command.startsWith("COMMAND_DOWNLOAD ")) {
                        String[] answer = command.split(" ", 2);
                        File file = new File(currentPath + answer[1]); // read file name
                        if (!file.exists()) {
                            file.createNewFile();
                        } else {
                            throw new IOException(answer[1] + " уже существует");
                        }

                        FileOutputStream fos = new FileOutputStream(file);
                        long size = in.readLong();
                        byte[] buffer = new byte[8 * 1024];
                        for (int i = 0; i < (size + (8 * 1024 - 1)) / (8 * 1024); i++) {
                            int read = in.read(buffer);
                            fos.write(buffer, 0, read);
                        }
                        out.flush();
                        fos.close();
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION, String.format("Отправка файла: %s, выполнена успешно", answer[1]), ButtonType.OK);
                            alert.setHeaderText(null);
                            alert.showAndWait();
                            loadTableList();
                        });
                    } else if (command.startsWith("COMMAND_AUTH_STATUS ")) {
                        String[] answer = command.split(" ", 2);
                        if (answer[1].equals("OK")) {
                            serverPart.setVisible(true);
                            auth.setVisible(!serverPart.isVisible());
                            getFolderTreeFromServer(answer[1]);
                            Platform.runLater(() -> {
                                regStage.close();
                            });
                        } else {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Логин или пароль не верный", ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                                loadTableList();
                            });
                        }
                    } else if (command.startsWith("COMMAND_REG_STATUS ")) {
                        String[] answer = command.split(" ", 2);
                        if (answer[1].equals("OK")) {
                            serverPart.setVisible(true);
                            auth.setVisible(!serverPart.isVisible());
                            getFolderTreeFromServer(answer[1]);
                            Platform.runLater(() -> {
                                regStage.close();
                            });
                        } else {
                            System.out.println("логин занят");
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Логин занят, просьба придумать другой", ButtonType.OK);
                                alert.setHeaderText(null);
                                alert.showAndWait();
                                loadTableList();
                            });
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Сокет закрыт");
                    break;
                }
            }
        }).start();
    }

    //метод отображения пути в серверной части окна
    private void changePath(String serverPath) {
        pathForWindow = (serverPath.substring(3));
        serverTextField.setText(pathForWindow);
    }

    // заполняем комбобокс дисками файловой системы на стороне клиента
    private void diskBoxChange() {
        diskBox.getItems().clear();
        for (Path p : FileSystems.getDefault().getRootDirectories()) {
            diskBox.getItems().add(p.toString());
        }
        diskBox.getSelectionModel().select(0); // устанавливаем диск по умолчанию
        currentPath = diskBox.getSelectionModel().getSelectedItem().toString();
    }

    //отрисовка списка файлов в клиентской части таблицы
    private void loadTableList() {
        String[] filesList = new File(currentPath.toString()).list();
        clientFileList.getItems().clear();
        for (int i = 0; i < filesList.length; i++) {
            clientFileList.getItems().add(filesList[i]);
        }
        clientTextField.setText(currentPath);
    }

    //отправка команды получения списка файлов в серверной части
    private void getFolderTreeFromServer(String serverPath) {
        this.serverPath = serverPath;
        try {
            out.writeUTF(String.format("COMMAND_GETTREE_FROM_SERVER %S", serverPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //метод подключения к серверу
    public void connect() {
        try {
            socket = new Socket(HOST, PORT);
            System.out.println("Connect success");
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            commandThreadStart();
            //getFolderTreeFromServer(serverPath);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //метод отключения от сервера
    private void disconnect() {
        if (socket != null) {
            try {
                System.out.println("Посылаю команду COMMAND_EXIT");
                out.writeUTF("COMMAND_EXIT");
                in.close();
                out.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //кнопка перехода в родительский каталог на стороне сервера
    public void btnServerFolderUp(ActionEvent actionEvent) {
        try {
            out.writeUTF("COMMAND_LEVEL_UP");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //кнопка перехода в родительский каталог на стороне клиента
    public void btnClientFolderUp(ActionEvent actionEvent) {
        Path upperPath = Paths.get(clientTextField.getText()).getParent();
        if (upperPath != null) {
            currentPath = upperPath.toString();
            if (!currentPath.endsWith("\\")) {
                currentPath = currentPath + "\\";
            }
            loadTableList();
        }
    }

    //реализация функции закрытия окна и выхода из приложения
    public void closeWindow(ActionEvent actionEvent) {
        disconnect();
        Platform.exit();
    }

    //кнопка смены дисков на стороне клиента
    public void selectDiskAction(ActionEvent actionEvent) {
        ComboBox<String> element = (ComboBox<String>) actionEvent.getSource();
        currentPath = element.getSelectionModel().getSelectedItem();
        loadTableList();
    }

    //копирование фалов
    public void copyFile(ActionEvent actionEvent) {
        if (serverPart.isVisible()) {
            //если выделена строка в клиентском списке, т.е копирование на сервер
            if (clientFileList.getSelectionModel().getSelectedItem() != null) {
                String selectedItem = clientFileList.getSelectionModel().getSelectedItem();
                ObservableList<String> items = serverFileList.getItems();
                for (String item : items) {
                    if (item.equals(selectedItem)) {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Файл с таким имененем уже существует", ButtonType.OK);
                        alert.setHeaderText(null);
                        alert.showAndWait();
                        return;
                    }
                }
                upload(selectedItem);
            } else if (serverFileList.getSelectionModel().getSelectedItem() != null) { //если выделена строка в серверном списке, т.е копирование на клиента
                String selectedItem = serverFileList.getSelectionModel().getSelectedItem();
                ObservableList<String> items = clientFileList.getItems();
                for (String item : items) {
                    if (item.equals(selectedItem)) {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Файл с таким имененем уже существует", ButtonType.OK);
                        alert.setHeaderText(null);
                        alert.showAndWait();
                        return;
                    }
                }
                try {
                    out.writeUTF("COMMAND_DOWNLOAD " + selectedItem);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Просьба выбрать файл или папку", ButtonType.OK);
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        } else {
            initRegWindow();
            regStage.show();
        }
    }

    //метод отправки файла на сервер
    private void upload(String file) {
        try {
            File selectedFile = new File(currentPath + file);
            if (!selectedFile.exists()) {
                throw new FileNotFoundException();
            }
            long fileLength = selectedFile.length();
            FileInputStream fis = new FileInputStream(selectedFile);
            out.writeUTF("COMMAND_UPLOAD " + file);
            out.writeLong(fileLength);
            int read = 0;
            byte[] buffer = new byte[8 * 1024];
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            fis.close();
            out.flush();
        } catch (FileNotFoundException e) {
            System.err.println("Файл не найден - " + currentPath + file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //удаление файла или папки
    public void removeFile(ActionEvent actionEvent) {
        String clientFile = clientFileList.getSelectionModel().getSelectedItem();
        String serverFile = serverFileList.getSelectionModel().getSelectedItem();
        if (clientFile != null || serverFile != null) {
            Alert alertDel = new Alert(Alert.AlertType.CONFIRMATION);
            alertDel.setTitle("Удаление");
            alertDel.setHeaderText("Вы действительно хотите удалить: " + (clientFile == null ? serverFile : clientFile));
            Optional<ButtonType> option = alertDel.showAndWait();
            if (option.get() == ButtonType.OK) {
                //удаление файла или папки на клиенте
                if (clientFileList.getSelectionModel().getSelectedItem() != null) {
                    Path path = Path.of((currentPath + clientFile));
                    try {
                        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.delete(file);
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                if (exc != null) {
                                    throw exc;
                                }
                                Files.delete(dir);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                        Alert alert = new Alert(Alert.AlertType.INFORMATION, String.format("%s удален", clientFile), ButtonType.OK);
                        alert.setHeaderText(null);
                        alert.showAndWait();
                        getFolderTreeFromServer(serverPath);
                        loadTableList();
                    } catch (IOException e) {
                        Alert alert = new Alert(Alert.AlertType.ERROR, String.format("Не удается удалить %s", e.getMessage()), ButtonType.OK);
                        alert.showAndWait();
                    }
                    //удаление файла или папки на сервере
                } else if (serverFileList.getSelectionModel().getSelectedItem() != null) {
                    try {
                        out.writeUTF(String.format("COMMAND_DELETE %s", serverFile));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Просьба выбрать файл или папку", ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }

    //перемещение
    public void moveFile(ActionEvent actionEvent) {
        String clientFile = clientFileList.getSelectionModel().getSelectedItem();
        String serverFile = serverFileList.getSelectionModel().getSelectedItem();
        if (clientFile != null || serverFile != null) {
                //удаление файла на клиенте
                if (clientFileList.getSelectionModel().getSelectedItem() != null) {
                    Path path = Path.of((currentPath + clientFile));
                    try {
                        copyFile(actionEvent);
                        ObservableList<String> items = serverFileList.getItems();
                        for (String item : items) {
                            if (item.equals(clientFile)) {
                                return;
                            }
                        }
                        Files.delete(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //удаление файла или папки на сервере
                } else if (serverFileList.getSelectionModel().getSelectedItem() != null) {
                    try {
                        copyFile(actionEvent);
                        ObservableList<String> items = clientFileList.getItems();
                        for (String item : items) {
                            if (item.equals(serverFile)) {
                                return;
                            }
                        }
                        out.writeUTF(String.format("COMMAND_DELETE_AFTERMOVE %s", serverFile));
                        getFolderTreeFromServer(serverPath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Просьба выбрать файл или папку", ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
        }

    }

    //Кнопка обновления списка файлов
    public void updateLists(ActionEvent actionEvent) {
        getFolderTreeFromServer(serverPath);
        loadTableList();
    }

    //создание директории на клиенте
    public void btnMkDirClient(ActionEvent actionEvent) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Создать папку");
        dialog.setHeaderText(null);
        dialog.setContentText("Имя:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            try {
                Files.createDirectory(Path.of(currentPath + name));
                loadTableList();
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Папка с таким имененем уже существует", ButtonType.OK);
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });
    }

    //создание директории на сервере
    public void btnMkDirServer(ActionEvent actionEvent) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Создать папку");
        dialog.setHeaderText(null);
        dialog.setContentText("Имя:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            try {
                out.writeUTF("COMMAND_NEW_FOLDER " + name);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    //создание окна регистарции
    private void initRegWindow() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/reg.fxml")); // прогружает содержимое окна
            Parent root = fxmlLoader.load();
            regController = fxmlLoader.getController();
            regController.setController(this);

            regStage = new Stage();
            regStage.setTitle("Регистрация");
            regStage.setScene(new Scene(root, 450, 235));
            regStage.initStyle(StageStyle.UTILITY); //убираем кнопки свернуть/развернуть
            regStage.initModality(Modality.APPLICATION_MODAL);//убираем возможность переключиться на основное окно
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void auth(ActionEvent actionEvent) {
        if (regStage == null) {
            initRegWindow();
        }
        regStage.show();
    }

    //авторизация
    public void authentication(String login, String password) {
        if (socket == null || socket.isClosed()) {
            connect();
        }
        try {
            out.writeUTF(String.format("COMMAND_AUTH %s %s", login, password));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //регистрация
    public void registration(String login, String password) {
        if (socket == null || socket.isClosed()) {
            connect();
        }
        try {
            out.writeUTF(String.format("COMMAND_REG %s %s", login, password));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //сменить пользователя
    @FXML
    public void exit(ActionEvent actionEvent) {
        serverPart.setVisible(false);
        auth.setVisible(!serverPart.isVisible());
        try {
            out.writeUTF("COMMAND_CHANGE_USER");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
