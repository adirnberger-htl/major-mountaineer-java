module org.example.majorgame {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires org.yaml.snakeyaml;

    opens org.example.majorgame.game to javafx.fxml, org.mockito;

    exports org.example.majorgame;
    exports org.example.majorgame.game;
}
