package com.simpleautomation.zone;

import com.mojang.serialization.Codec;
import com.simpleautomation.job.BarnJob;
import com.simpleautomation.job.CrusherJob;
import com.simpleautomation.job.FarmJob;
import com.simpleautomation.job.FisheryJob;
import com.simpleautomation.job.ForgeJob;
import com.simpleautomation.job.Job;
import com.simpleautomation.job.LumberJob;
import com.simpleautomation.job.QuarryJob;
import java.util.function.Supplier;
import net.minecraft.util.StringRepresentable;

/** Métier d'un contrôleur : chaque bloc de contrôle a le sien. */
public enum JobType implements StringRepresentable {
    QUARRY("quarry", QuarryJob::new),
    LUMBER("lumber", LumberJob::new),
    FORGE("forge", ForgeJob::new),
    CRUSHER("crusher", CrusherJob::new),
    FARM("farm", FarmJob::new),
    BARN("barn", BarnJob::new),
    FISHERY("fishery", FisheryJob::new);

    public static final Codec<JobType> CODEC = StringRepresentable.fromEnum(JobType::values);

    private final String name;
    private final Supplier<Job> factory;

    JobType(String name, Supplier<Job> factory) {
        this.name = name;
        this.factory = factory;
    }

    public Job create() {
        return factory.get();
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
