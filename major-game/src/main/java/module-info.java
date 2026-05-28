module org.example.majorgame {
    requires java.sql;
    requires javafx.controls;
    requires javafx.fxml;
    requires org.xerial.sqlitejdbc;
    requires org.yaml.snakeyaml;


    opens org.example.majorgame to javafx.fxml;
    exports org.example.majorgame;
    exports org.example.majorgame.game;
}
