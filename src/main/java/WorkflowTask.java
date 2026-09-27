import java.util.ArrayList;
import java.util.List;

public class WorkflowTask {

    private final int id;
    private final long instructionLength;

    private final List<WorkflowTask> predecessors;
    private final List<WorkflowTask> successors;
    private final List<Double> communicationCosts;

    private double rankUpward;

    private int assignedVmId = -1;

    private double startTime = 0.0;
    private double finishTime = 0.0;

    public WorkflowTask(int id, long instructionLength) {
        this.id = id;
        this.instructionLength = instructionLength;

        this.predecessors = new ArrayList<>();
        this.successors = new ArrayList<>();
        this.communicationCosts = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public long getInstructionLength() {
        return instructionLength;
    }

    public List<WorkflowTask> getPredecessors() {
        return predecessors;
    }

    public List<WorkflowTask> getSuccessors() {
        return successors;
    }

    public List<Double> getCommunicationCosts() {
        return communicationCosts;
    }

    public double getRankUpward() {
        return rankUpward;
    }

    public void setRankUpward(double rankUpward) {
        this.rankUpward = rankUpward;
    }

    public int getAssignedVmId() {
        return assignedVmId;
    }

    public void setAssignedVmId(int assignedVmId) {
        this.assignedVmId = assignedVmId;
    }

    public double getStartTime() {
        return startTime;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime;
    }

    public double getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(double finishTime) {
        this.finishTime = finishTime;
    }

    /**
     * communicationCost disimpan sebagai DATA SIZE dalam MB.
     */
    public void addSuccessor(
        WorkflowTask successor,
        double dataSizeMB
    ) {
        successors.add(successor);
        communicationCosts.add(dataSizeMB);
        successor.predecessors.add(this);
    }

    public double getCommunicationCostTo(
        WorkflowTask successor
    ) {
        for (int i = 0; i < successors.size(); i++) {
            if (successors.get(i).getId()
                == successor.getId()) {

                return communicationCosts.get(i);
            }
        }

        return 0.0;
    }

    @Override
    public String toString() {
        return String.format(
            "Task #%d | MI=%d | rank_u=%.4f | VM=%d | START=%.4f | FINISH=%.4f",
            id,
            instructionLength,
            rankUpward,
            assignedVmId,
            startTime,
            finishTime
        );
    }
}