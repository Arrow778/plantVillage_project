import os

def check_dataset():
    root = r"e:\Code\google_code\Antigravity\plantVillage\trian_code\dataset_plant\data1"
    if not os.path.exists(root):
        print(f"Error: Path {root} not found.")
        return
        
    dirs = sorted([d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d))])
    
    with open("dataset_check_output.txt", "w", encoding="utf-8") as f:
        f.write(f"Total classes found: {len(dirs)}\n")
        f.write("-" * 50 + "\n")
        for i, d in enumerate(dirs):
            count = len(os.listdir(os.path.join(root, d)))
            f.write(f"Index {i:2d}: {d} (Images: {count})\n")

if __name__ == "__main__":
    check_dataset()
