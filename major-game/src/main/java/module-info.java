module org.example.majorgame {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.yaml.snakeyaml;


    opens org.example.majorgame to javafx.fxml;
    exports org.example.majorgame;
    exports org.example.majorgame.game;
}
