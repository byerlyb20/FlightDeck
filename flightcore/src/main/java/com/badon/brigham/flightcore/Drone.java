package com.badon.brigham.flightcore;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

@Entity
public class Drone {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String familiarName;
    public String ipAddr;
}
