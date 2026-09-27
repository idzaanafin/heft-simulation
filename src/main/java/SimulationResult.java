public class SimulationResult {

    private final int taskCount;
    private final int vmCount;

    private final double makespan;
    private final double cpuUtilization;
    private final double ramUtilization;
    private final double energy;

    private final long schedulingRuntimeMs;

    public SimulationResult(
        int taskCount,
        int vmCount,
        double makespan,
        double cpuUtilization,
        double ramUtilization,
        double energy,
        long schedulingRuntimeMs
    ) {

        this.taskCount = taskCount;
        this.vmCount = vmCount;
        this.makespan = makespan;
        this.cpuUtilization = cpuUtilization;
        this.ramUtilization = ramUtilization;
        this.energy = energy;
        this.schedulingRuntimeMs =
            schedulingRuntimeMs;
    }

    public void print() {

        System.out.println();
        System.out.println(
            "=== FINAL SIMULATION RESULT ==="
        );

        System.out.printf(
            "Tasks               : %d%n",
            taskCount
        );

        System.out.printf(
            "VMs                 : %d%n",
            vmCount
        );

        System.out.printf(
            "Actual Makespan     : %.6f seconds%n",
            makespan
        );

        System.out.printf(
            "CPU Utilization     : %.2f%%%n",
            cpuUtilization
        );

        System.out.printf(
            "RAM Utilization     : %.2f%%%n",
            ramUtilization
        );

        System.out.printf(
            "Energy              : %.4f Joule%n",
            energy
        );

        System.out.printf(
            "HEFT Runtime        : %d ms%n",
            schedulingRuntimeMs
        );
    }

    public double getMakespan() {
        return makespan;
    }

    public double getEnergy() {
        return energy;
    }
}