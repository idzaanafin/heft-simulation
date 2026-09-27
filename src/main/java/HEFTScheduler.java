import org.cloudbus.cloudsim.Vm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HEFTScheduler {

    /*
     * Model network untuk HEFT.
     *
     * Data size      : MB
     * Bandwidth      : MB/s
     */
    public static final double
        NETWORK_BANDWIDTH_MBPS = 100.0;

    private final WorkflowDAG dag;
    private final List<Vm> vms;

    private final Map<
        Integer,
        List<TaskInterval>
    > vmSchedules;

    private static class TaskInterval {

        private final WorkflowTask task;
        private final double start;
        private final double finish;

        public TaskInterval(
            WorkflowTask task,
            double start,
            double finish
        ) {
            this.task = task;
            this.start = start;
            this.finish = finish;
        }

        public WorkflowTask getTask() {
            return task;
        }

        public double getStart() {
            return start;
        }

        public double getFinish() {
            return finish;
        }
    }

    public HEFTScheduler(
        WorkflowDAG dag,
        List<Vm> vms
    ) {

        this.dag = dag;
        this.vms = vms;

        vmSchedules = new HashMap<>();

        for (Vm vm : vms) {
            vmSchedules.put(
                vm.getId(),
                new ArrayList<>()
            );
        }
    }

    /*
     * =====================================================
     * COMPUTATION
     * =====================================================
     */

    private double computationCost(
        WorkflowTask task,
        Vm vm
    ) {

        return (double)
            task.getInstructionLength()
            / vm.getMips();
    }

    private double averageComputationCost(
        WorkflowTask task
    ) {

        double total = 0.0;

        for (Vm vm : vms) {
            total +=
                computationCost(
                    task,
                    vm
                );
        }

        return total / vms.size();
    }

    /*
     * =====================================================
     * COMMUNICATION
     * =====================================================
     */

    private double dataSize(
        WorkflowTask from,
        WorkflowTask to
    ) {

        return from.getCommunicationCostTo(to);
    }

    private double communicationCost(
        WorkflowTask from,
        WorkflowTask to,
        Vm sourceVm,
        Vm destinationVm
    ) {

        /*
         * Same VM:
         * no network transfer.
         */
        if (sourceVm.getId()
            == destinationVm.getId()) {

            return 0.0;
        }

        /*
         * Different VM:
         * communication delay =
         * data size / network bandwidth
         */
        return dataSize(from, to)
            / NETWORK_BANDWIDTH_MBPS;
    }

    /*
     * Average communication cost used
     * by the HEFT upward-rank calculation.
     *
     * HEFT averages communication cost
     * over all source/destination processor
     * combinations.
     *
     * Same VM:
     *     communication = 0
     *
     * Different VM:
     *     communication = dataSize / bandwidth
     */
    private double averageCommunicationCost(
        WorkflowTask from,
        WorkflowTask to
    ) {

        if (vms.size() <= 1) {
            return 0.0;
        }

        double networkCost =
            dataSize(from, to)
            / NETWORK_BANDWIDTH_MBPS;

        /*
         * Number of VM pairs with
         * different source and destination:
         *
         * P * (P - 1)
         *
         * Total VM pairs:
         *
         * P * P
         *
         * Therefore:
         *
         * average =
         * networkCost * (P - 1) / P
         */
        double differentVmProbability =
            (double) (vms.size() - 1)
            / vms.size();

        return networkCost
            * differentVmProbability;
    }

    /*
     * =====================================================
     * UPWARD RANK
     * =====================================================
     */

    private double calculateRankUpward(
        WorkflowTask task,
        Map<Integer, Double> memo
    ) {

        if (memo.containsKey(task.getId())) {
            return memo.get(task.getId());
        }

        double averageComputation =
            averageComputationCost(task);

        double maxSuccessorCost = 0.0;

        for (WorkflowTask successor :
            task.getSuccessors()) {

            double successorRank =
                calculateRankUpward(
                    successor,
                    memo
                );

            double communication =
                averageCommunicationCost(
                    task,
                    successor
                );

            maxSuccessorCost =
                Math.max(
                    maxSuccessorCost,
                    communication
                    + successorRank
                );
        }

        double rank =
            averageComputation
            + maxSuccessorCost;

        task.setRankUpward(rank);

        memo.put(
            task.getId(),
            rank
        );

        return rank;
    }

    public void calculateRanks() {

        Map<Integer, Double> memo =
            new HashMap<>();

        for (WorkflowTask task :
            dag.getTasks()) {

            calculateRankUpward(
                task,
                memo
            );
        }
    }

    /*
     * =====================================================
     * PRIORITY
     * =====================================================
     */

    public List<WorkflowTask>
    getPriorityOrder() {

        List<WorkflowTask> ordered =
            new ArrayList<>(
                dag.getTasks()
            );

        ordered.sort(
            Comparator
                .comparingDouble(
                    WorkflowTask::getRankUpward
                )
                .reversed()
                .thenComparingInt(
                    WorkflowTask::getId
                )
        );

        return ordered;
    }

    /*
     * =====================================================
     * VM LOOKUP
     * =====================================================
     */

    private Vm getVmById(int id) {

        for (Vm vm : vms) {

            if (vm.getId() == id) {
                return vm;
            }
        }

        throw new IllegalArgumentException(
            "VM #" + id + " tidak ditemukan."
        );
    }

    /*
     * =====================================================
     * READY TIME
     * =====================================================
     */

    private double calculateReadyTime(
        WorkflowTask task,
        Vm candidateVm
    ) {

        double readyTime = 0.0;

        for (WorkflowTask predecessor :
            task.getPredecessors()) {

            if (predecessor.getAssignedVmId()
                < 0) {

                throw new IllegalStateException(
                    "Predecessor Task #"
                    + predecessor.getId()
                    + " belum dijadwalkan."
                );
            }

            Vm predecessorVm =
                getVmById(
                    predecessor.getAssignedVmId()
                );

            double communication =
                communicationCost(
                    predecessor,
                    task,
                    predecessorVm,
                    candidateVm
                );

            double arrival =
                predecessor.getFinishTime()
                + communication;

            readyTime =
                Math.max(
                    readyTime,
                    arrival
                );
        }

        return readyTime;
    }

    /*
     * =====================================================
     * INSERTION POLICY
     * =====================================================
     */

    private double findEarliestStart(
        WorkflowTask task,
        Vm vm
    ) {

        double readyTime =
            calculateReadyTime(
                task,
                vm
            );

        double executionTime =
            computationCost(
                task,
                vm
            );

        List<TaskInterval> schedule =
            vmSchedules.get(
                vm.getId()
            );

        schedule.sort(
            Comparator.comparingDouble(
                TaskInterval::getStart
            )
        );

        /*
         * Initially, the task can start
         * when all predecessors have
         * finished and their data has arrived.
         */
        double candidate =
            readyTime;

        for (TaskInterval interval :
            schedule) {

            double candidateFinish =
                candidate
                + executionTime;

            /*
             * The task fits completely
             * inside the current gap.
             *
             * This is the insertion policy
             * used by classical HEFT.
             */
            if (candidateFinish
                <= interval.getStart()) {

                return candidate;
            }

            /*
             * Otherwise move the candidate
             * start time behind this interval.
             */
            candidate =
                Math.max(
                    candidate,
                    interval.getFinish()
                );
        }

        /*
         * No gap was available.
         * Append the task at the end.
         */
        return candidate;
    }

    private void insertTask(
        WorkflowTask task,
        Vm vm,
        double start,
        double finish
    ) {

        List<TaskInterval> schedule =
            vmSchedules.get(
                vm.getId()
            );

        schedule.add(
            new TaskInterval(
                task,
                start,
                finish
            )
        );

        schedule.sort(
            Comparator.comparingDouble(
                TaskInterval::getStart
            )
        );
    }

    /*
     * =====================================================
     * HEFT
     * =====================================================
     */

    public void schedule() {

        calculateRanks();

        List<WorkflowTask> ordered =
            getPriorityOrder();

        for (WorkflowTask task :
            ordered) {

            Vm selectedVm = null;

            double selectedStart =
                Double.MAX_VALUE;

            double selectedEft =
                Double.MAX_VALUE;

            /*
             * HEFT processor selection:
             *
             * For every VM:
             *
             *     EST = earliest start time
             *     EFT = EST + execution time
             *
             * Select VM with minimum EFT.
             */
            for (Vm vm : vms) {

                double start =
                    findEarliestStart(
                        task,
                        vm
                    );

                double executionTime =
                    computationCost(
                        task,
                        vm
                    );

                double eft =
                    start
                    + executionTime;

                if (eft < selectedEft) {

                    selectedVm = vm;
                    selectedStart = start;
                    selectedEft = eft;
                }
            }

            if (selectedVm == null) {

                throw new IllegalStateException(
                    "Tidak ada VM untuk Task #"
                    + task.getId()
                );
            }

            /*
             * Save HEFT scheduling result
             * into the workflow task.
             */
            task.setAssignedVmId(
                selectedVm.getId()
            );

            task.setStartTime(
                selectedStart
            );

            task.setFinishTime(
                selectedEft
            );

            /*
             * Insert the task into the selected
             * VM schedule.
             */
            insertTask(
                task,
                selectedVm,
                selectedStart,
                selectedEft
            );
        }
    }

    /*
     * =====================================================
     * RESULTS
     * =====================================================
     */

    public double getMakespan() {

        double makespan = 0.0;

        for (WorkflowTask task :
            dag.getTasks()) {

            makespan =
                Math.max(
                    makespan,
                    task.getFinishTime()
                );
        }

        return makespan;
    }

    public void printPriorityOrder() {

        System.out.println();
        System.out.println(
            "=== HEFT PRIORITY ORDER ==="
        );

        for (WorkflowTask task :
            getPriorityOrder()) {

            System.out.printf(
                "Task #%d | rank_u = %.6f%n",
                task.getId(),
                task.getRankUpward()
            );
        }
    }

    public void printSchedule() {

        System.out.println();
        System.out.println(
            "=== HEFT TASK SCHEDULE ==="
        );

        List<WorkflowTask> tasks =
            new ArrayList<>(
                dag.getTasks()
            );

        tasks.sort(
            Comparator.comparingInt(
                WorkflowTask::getId
            )
        );

        for (WorkflowTask task :
            tasks) {

            System.out.printf(
                "Task #%d | MI=%d | rank=%.4f | "
                + "VM=%d | START=%.4f | FINISH=%.4f%n",

                task.getId(),
                task.getInstructionLength(),
                task.getRankUpward(),
                task.getAssignedVmId(),
                task.getStartTime(),
                task.getFinishTime()
            );
        }

        System.out.println();

        System.out.printf(
            "HEFT Estimated Makespan : %.6f seconds%n",
            getMakespan()
        );
    }

    public void printVmSchedules() {

        System.out.println();
        System.out.println(
            "=== VM SCHEDULE ==="
        );

        for (Vm vm : vms) {

            List<TaskInterval> schedule =
                vmSchedules.get(
                    vm.getId()
                );

            System.out.printf(
                "VM #%d:%n",
                vm.getId()
            );

            for (TaskInterval interval :
                schedule) {

                System.out.printf(
                    "  Task #%d | %.4f -> %.4f%n",
                    interval.getTask().getId(),
                    interval.getStart(),
                    interval.getFinish()
                );
            }
        }
    }
}