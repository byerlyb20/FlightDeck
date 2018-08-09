package com.badon.brigham.flightcore;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface DroneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public void insertUsers(Drone drone);

    @Query("SELECT * FROM drone")
    public Drone[] loadAllDrones();

    @Query("SELECT * FROM drone")
    public LiveData<List<Drone>> loadAllDronesSync();
}
