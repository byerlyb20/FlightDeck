package com.badon.brigham.flightcore;

import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

public abstract class DroneDatabase extends RoomDatabase {

    private static DroneDatabase sMainInstance;

    public static DroneDatabase getInstance(Context context) {
        if (sMainInstance == null) {
            sMainInstance = Room.databaseBuilder(context,
                    DroneDatabase.class, "flightcore-db").build();
        }

        return sMainInstance;
    }

    public abstract DroneDao droneDao();

}
