package com.example.ble_esp;
import androidx.room.*;
import androidx.lifecycle.LiveData;
import java.util.List;

@Dao
public interface MeasurementDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) void insertAll(List<Measurement> rows);
    @Query("SELECT * FROM mediciones WHERE equipoId = :team ORDER BY fechaHora DESC, medicionId LIMIT 100")
    LiveData<List<Measurement>> recent(String team);
    @Query("SELECT COUNT(*) FROM mediciones WHERE equipoId = :team AND sincronizado = 0")
    LiveData<Integer> pendingCount(String team);
    @Query("SELECT * FROM mediciones WHERE equipoId = :team AND sincronizado = 0 ORDER BY fechaHora LIMIT 100")
    List<Measurement> pending(String team);
    @Query("UPDATE mediciones SET sincronizado = 1 WHERE medicionId IN (:ids)")
    void markSynced(List<String> ids);
}
