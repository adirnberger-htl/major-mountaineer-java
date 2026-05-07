package org.example.majorgame;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MajorGameApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MajorGameApplication.class.getResource("major-game-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 760, 560);
        scene.getStylesheets().add(MajorGameApplication.class.getResource("mountaineer-theme.css").toExternalForm());
        stage.setTitle("Major Mountaineer");
        stage.setScene(scene);
        stage.show();
    }
}
