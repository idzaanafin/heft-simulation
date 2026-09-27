import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class WorkflowDatasetLoader {

    private WorkflowDatasetLoader() {
    }

    public static WorkflowDAG loadDax(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path)) {
            return parse(input);
        }
    }

    private static WorkflowDAG parse(InputStream input) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(input);

        NodeList jobs = document.getElementsByTagName("job");
        WorkflowDAG dag = new WorkflowDAG();
        Map<String, WorkflowTask> tasks = new HashMap<>();
        Map<String, Double> outputSizes = new HashMap<>();

        for (int i = 0; i < jobs.getLength(); i++) {
            Element job = (Element) jobs.item(i);
            String id = job.getAttribute("id");
            double runtime = Double.parseDouble(job.getAttribute("runtime"));
            long instructionLength = Math.max(1L, Math.round(runtime * 1000.0));
            WorkflowTask task = new WorkflowTask(i, instructionLength);
            dag.addTask(task);
            tasks.put(id, task);
            outputSizes.put(id, findOutputSize(job));
        }

        NodeList children = document.getElementsByTagName("child");
        for (int i = 0; i < children.getLength(); i++) {
            Element childElement = (Element) children.item(i);
            WorkflowTask child = tasks.get(childElement.getAttribute("ref"));
            if (child == null) {
                throw new IllegalArgumentException("Unknown child job: "
                    + childElement.getAttribute("ref"));
            }

            NodeList parents = childElement.getElementsByTagName("parent");
            for (int p = 0; p < parents.getLength(); p++) {
                Element parentElement = (Element) parents.item(p);
                WorkflowTask parent = tasks.get(parentElement.getAttribute("ref"));
                if (parent == null) {
                    throw new IllegalArgumentException("Unknown parent job: "
                        + parentElement.getAttribute("ref"));
                }
                double dataSize = outputSizes.getOrDefault(
                    parentElement.getAttribute("ref"), 10.0);
                parent.addSuccessor(child, dataSize);
            }
        }

        return dag;
    }

    private static double findOutputSize(Element job) {
        NodeList files = job.getElementsByTagName("uses");
        for (int i = 0; i < files.getLength(); i++) {
            Element file = (Element) files.item(i);
            if ("output".equals(file.getAttribute("link"))) {
                return Math.max(1.0, Double.parseDouble(file.getAttribute("size")) / 1_000_000.0);
            }
        }
        return 10.0;
    }
}
