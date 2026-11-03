package com.example.ble_esp;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "mediciones")
public class Measurement {
    @PrimaryKey @NonNull public String medicionId = "";
    @NonNull public String equipoId = "";
    @NonNull public String dispositivoId = "";
    @NonNull public String origen = "";
    @NonNull public String variable = "";
    public double valor;
    @NonNull public String unidad = "";
    public long fechaHora;
    public boolean sincronizado;
}
