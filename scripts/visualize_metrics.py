"""Generate Bab IV comparison charts from results/dataset_metrics.csv."""

from pathlib import Path
import csv

import matplotlib.pyplot as plt


PROJECT_ROOT = Path(__file__).resolve().parents[1]
INPUT_FILE = PROJECT_ROOT / "results" / "dataset_metrics.csv"
OUTPUT_DIR = PROJECT_ROOT / "results" / "figures"


def values(data, column):
    return [row[column] for row in data]


def add_value_labels(axis, bars, formatter):
    for bar in bars:
        value = bar.get_height()
        axis.annotate(
            formatter(value),
            (bar.get_x() + bar.get_width() / 2, value),
            xytext=(0, 4),
            textcoords="offset points",
            ha="center",
            va="bottom",
            fontsize=8,
        )


def make_chart(data, column, title, ylabel, filename, formatter):
    figure, axis = plt.subplots(figsize=(8, 5))
    colors = ["#2563eb", "#dc2626", "#16a34a"]
    bars = axis.bar(
        values(data, "dataset"),
        values(data, column),
        color=colors,
        width=0.62,
    )
    axis.set_title(title, fontweight="bold")
    axis.set_ylabel(ylabel)
    axis.grid(axis="y", linestyle="--", alpha=0.3)
    axis.set_axisbelow(True)
    add_value_labels(axis, bars, formatter)
    figure.tight_layout()
    figure.savefig(OUTPUT_DIR / filename, dpi=220, bbox_inches="tight")
    plt.close(figure)


def make_dashboard(data):
    figure, axes = plt.subplots(2, 2, figsize=(13, 9))
    colors = ["#2563eb", "#dc2626", "#16a34a"]

    charts = [
        ("makespan_seconds", "Makespan", "Detik", ".2f"),
        ("energy_joule", "Konsumsi Energi", "Joule", ",.0f"),
        ("heft_runtime_ms", "Runtime Scheduler HEFT", "Milidetik", ".0f"),
        ("cpu_utilization_percent", "Utilisasi CPU dan RAM", "Persen", ".2f"),
    ]

    for index, (column, title, ylabel, format_spec) in enumerate(charts):
        axis = axes.flat[index]
        if column == "cpu_utilization_percent":
            positions = list(range(len(data)))
            cpu_bars = axis.bar(
                positions,
                values(data, column),
                color=colors,
                width=0.35,
                label="CPU",
            )
            ram_bars = axis.bar(
                [index + 0.35 for index in positions],
                values(data, "ram_utilization_percent"),
                color="#f59e0b",
                width=0.35,
                label="RAM",
            )
            axis.set_xticks([index + 0.175 for index in positions])
            axis.set_xticklabels(values(data, "dataset"))
            add_value_labels(axis, cpu_bars, lambda value: f"{value:.2f}")
            add_value_labels(axis, ram_bars, lambda value: f"{value:.2f}")
            axis.legend()
        else:
            bars = axis.bar(
                values(data, "dataset"),
                values(data, column),
                color=colors,
                width=0.62,
            )
            add_value_labels(
                axis,
                bars,
                lambda value, spec=format_spec: format(value, spec),
            )
        axis.set_title(title, fontweight="bold")
        axis.set_ylabel(ylabel)
        axis.grid(axis="y", linestyle="--", alpha=0.3)
        axis.set_axisbelow(True)

    figure.suptitle(
        "Perbandingan Metrik Evaluasi HEFT (200 Task, 80 VM)",
        fontsize=15,
        fontweight="bold",
    )
    figure.tight_layout()
    figure.savefig(OUTPUT_DIR / "bab4-metrik-evaluasi-dashboard.png", dpi=220, bbox_inches="tight")
    plt.close(figure)


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    with INPUT_FILE.open(newline="", encoding="utf-8") as file:
        data = list(csv.DictReader(file))
    for row in data:
        for key, value in row.items():
            if key != "dataset":
                row[key] = float(value)
    make_chart(
        data,
        "makespan_seconds",
        "Perbandingan Makespan",
        "Makespan (detik)",
        "makespan.png",
        lambda value: f"{value:.2f}",
    )
    make_chart(
        data,
        "energy_joule",
        "Perbandingan Konsumsi Energi",
        "Energi (Joule)",
        "energy.png",
        lambda value: f"{value:,.0f}",
    )
    make_chart(
        data,
        "heft_runtime_ms",
        "Perbandingan Runtime Scheduler",
        "Runtime (ms)",
        "heft-runtime.png",
        lambda value: f"{value:.0f}",
    )
    make_chart(
        data,
        "cpu_utilization_percent",
        "Perbandingan Utilisasi CPU",
        "Utilisasi CPU (%)",
        "cpu-utilization.png",
        lambda value: f"{value:.2f}",
    )
    make_chart(
        data,
        "ram_utilization_percent",
        "Perbandingan Utilisasi RAM",
        "Utilisasi RAM (%)",
        "ram-utilization.png",
        lambda value: f"{value:.2f}",
    )
    make_chart(
        data,
        "throughput_tasks_per_second",
        "Perbandingan Throughput",
        "Throughput (task/detik)",
        "throughput.png",
        lambda value: f"{value:.2f}",
    )
    make_dashboard(data)
    print(f"Generated charts in {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
