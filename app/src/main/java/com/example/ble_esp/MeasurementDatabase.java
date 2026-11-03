package com.example.ble_esp;
import androidx.room.Database;
import androidx.room.RoomDatabase;
@Database(entities = {Measurement.class}, version = 1, exportSchema = false)
public abstract class MeasurementDatabase extends RoomDatabase {
    public abstract MeasurementDao measurements();
}
