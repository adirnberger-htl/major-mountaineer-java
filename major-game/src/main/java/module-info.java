module org.example.majorgame {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires org.yaml.snakeyaml;


    opens org.example.majorgame to javafx.fxml;

    exports org.example.majorgame;
    exports org.example.majorgame.game;
    exports org.example.majorgame.game.combat;
}
