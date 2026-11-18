import SwiftUI

@main
struct FolioApp: App {
    @StateObject private var store = NoteStore()

    var body: some Scene {
        WindowGroup {
            FolioRootView()
                .environmentObject(store)
                .frame(minWidth: 900, minHeight: 620)
        }
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("New Note") { store.createNote() }
                    .keyboardShortcut("n", modifiers: .command)
            }
        }
        Settings {
            SettingsView()
                .environmentObject(store)
        }
    }
}

struct FolioNote: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var title: String
    var body: String
    var folder: String = "Notes"
    var tags: [String] = []
    var isPinned: Bool = false
    var isArchived: Bool = false
    var isLocked: Bool = false
    var updatedAt: Date = .now
}

@MainActor
final class NoteStore: ObservableObject {
    @Published var notes: [FolioNote] = []
    @Published var selection: UUID?
    @Published var query = ""
    @Published var selectedFolder = "All Notes"

    private let fileURL: URL

    init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let directory = base.appendingPathComponent("Folio", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        fileURL = directory.appendingPathComponent("notes.json")
        load()
        if notes.isEmpty { createNote(title: "Welcome to Folio") }
    }

    var visibleNotes: [FolioNote] {
        notes.filter { note in
            let folderMatch = selectedFolder == "All Notes" || note.folder == selectedFolder
            let queryMatch = query.isEmpty || note.title.localizedCaseInsensitiveContains(query) || note.body.localizedCaseInsensitiveContains(query) || note.tags.contains(where: { $0.localizedCaseInsensitiveContains(query) })
            return folderMatch && queryMatch && !note.isArchived && !note.isLocked
        }.sorted { ($0.isPinned, $0.updatedAt) > ($1.isPinned, $1.updatedAt) }
    }

    var selectedNote: FolioNote? { notes.first(where: { $0.id == selection }) }

    func createNote(title: String = "Untitled Note") {
        let note = FolioNote(title: title, body: "")
        notes.insert(note, at: 0)
        selection = note.id
        save()
    }

    func updateSelected(title: String, body: String) {
        guard let id = selection, let index = notes.firstIndex(where: { $0.id == id }) else { return }
        notes[index].title = title
        notes[index].body = body
        notes[index].updatedAt = .now
        save()
    }

    func togglePin(_ note: FolioNote) {
        guard let index = notes.firstIndex(where: { $0.id == note.id }) else { return }
        notes[index].isPinned.toggle()
        notes[index].updatedAt = .now
        save()
    }

    func delete(_ note: FolioNote) {
        notes.removeAll { $0.id == note.id }
        if selection == note.id { selection = visibleNotes.first?.id }
        save()
    }

    private func load() {
        guard let data = try? Data(contentsOf: fileURL),
              let decoded = try? JSONDecoder().decode([FolioNote].self, from: data) else { return }
        notes = decoded
        selection = notes.first?.id
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(notes) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }
}

struct FolioRootView: View {
    @EnvironmentObject private var store: NoteStore
    @State private var draftTitle = ""
    @State private var draftBody = ""

    private let folders = ["All Notes", "Notes", "Work", "Personal", "Study", "Archive"]

    var body: some View {
        NavigationSplitView {
            VStack(spacing: 12) {
                HStack {
                    Text("FOLIO").font(.system(size: 20, weight: .bold, design: .rounded))
                    Spacer()
                    Button { store.createNote() } label: { Image(systemName: "square.and.pencil") }
                        .help("New note")
                }
                .padding(.horizontal, 14).padding(.top, 12)
                ForEach(folders, id: \.self) { folder in
                    Button {
                        store.selectedFolder = folder == "Archive" ? "All Notes" : folder
                    } label: {
                        Label(folder, systemImage: folder == "All Notes" ? "note.text" : "folder")
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(8)
                            .background(store.selectedFolder == folder ? Color.accentColor.opacity(0.14) : .clear, in: RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
                Label("Local storage", systemImage: "internaldrive")
                    .font(.caption).foregroundStyle(.secondary)
                    .padding(.bottom, 10)
            }
            .padding(10)
            .navigationSplitViewColumnWidth(min: 170, ideal: 205)
        } content: {
            VStack(spacing: 10) {
                TextField("Search notes", text: $store.query)
                    .textFieldStyle(.roundedBorder)
                List(selection: $store.selection) {
                    ForEach(store.visibleNotes) { note in
                        VStack(alignment: .leading, spacing: 5) {
                            HStack {
                                Text(note.title.isEmpty ? "Untitled Note" : note.title)
                                    .font(.headline).lineLimit(1)
                                if note.isPinned { Image(systemName: "pin.fill").foregroundStyle(.orange) }
                            }
                            Text(note.body.isEmpty ? "No additional text" : note.body)
                                .font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                            Text(note.updatedAt, style: .date)
                                .font(.caption2).foregroundStyle(.tertiary)
                        }
                        .tag(note.id)
                        .contextMenu {
                            Button(note.isPinned ? "Unpin" : "Pin") { store.togglePin(note) }
                            Button("Delete", role: .destructive) { store.delete(note) }
                        }
                    }
                }
                .listStyle(.plain)
            }
            .padding(12)
            .navigationSplitViewColumnWidth(min: 230, ideal: 285)
        } detail: {
            if let note = store.selectedNote {
                NoteEditor(note: note)
            } else {
                ContentUnavailableView("Select a note", systemImage: "note.text", description: Text("Create a note or choose one from the list."))
            }
        }
        .onChange(of: store.selection) { _ in syncDraft() }
        .onAppear { syncDraft() }
    }

    private func syncDraft() {
        draftTitle = store.selectedNote?.title ?? ""
        draftBody = store.selectedNote?.body ?? ""
    }
}

struct NoteEditor: View {
    @EnvironmentObject private var store: NoteStore
    let note: FolioNote
    @State private var title = ""
    @State private var bodyText = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                TextField("Title", text: $title)
                    .font(.system(size: 28, weight: .bold))
                    .textFieldStyle(.plain)
                Spacer()
                Button { store.togglePin(note) } label: {
                    Image(systemName: note.isPinned ? "pin.fill" : "pin")
                }
                Button(role: .destructive) { store.delete(note) } label: { Image(systemName: "trash") }
            }
            TextEditor(text: $bodyText)
                .font(.system(size: 15))
                .scrollContentBackground(.hidden)
                .onChange(of: title) { _ in persist() }
                .onChange(of: bodyText) { _ in persist() }
            HStack {
                Text("Autosaved locally")
                Spacer()
                Text("\(bodyText.split(whereSeparator: \.isWhitespace).count) words")
            }
            .font(.caption).foregroundStyle(.secondary)
        }
        .padding(24)
        .onAppear { title = note.title; bodyText = note.body }
        .onChange(of: note.id) { _ in title = note.title; bodyText = note.body }
    }

    private func persist() { store.updateSelected(title: title, body: bodyText) }
}

struct SettingsView: View {
    var body: some View {
        Form {
            Section("Folio") {
                Text("Folio for macOS")
                Text("Local notes are stored in Application Support/Folio.")
                    .foregroundStyle(.secondary)
            }
            Section("Coming modules") {
                Text("Sync, Safe Folder, rich-text blocks, PDF, audio, drawing and AI modules are being built against the shared document contract.")
            }
        }
        .padding(20)
        .frame(width: 440)
    }
}
