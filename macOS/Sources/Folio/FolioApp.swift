import SwiftUI
import Combine

@main
struct FolioApp: App {
    @StateObject private var store = NoteStore()
    var body: some Scene {
        WindowGroup { FolioRootView().environmentObject(store).frame(minWidth: 980, minHeight: 650) }
            .commands {
                CommandGroup(replacing: .newItem) {
                    Button("New Note") { store.createNote() }.keyboardShortcut("n", modifiers: .command)
                }
                CommandGroup(after: .textEditing) {
                    Button("Toggle Pin") { store.togglePinSelected() }.keyboardShortcut("p", modifiers: [.command, .shift])
                }
            }
        Settings { SettingsView().environmentObject(store) }
    }
}

struct FolioNote: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var title: String
    var body: String
    var folder: String = "Notes"
    var tags: [String] = []
    var isPinned = false
    var isArchived = false
    var isDeleted = false
    var createdAt: Date = .now
    var updatedAt: Date = .now

    private enum CodingKeys: String, CodingKey {
        case id, title, body, folder, tags, isPinned, isArchived, isDeleted, createdAt, updatedAt
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        title = try values.decodeIfPresent(String.self, forKey: .title) ?? "Untitled Note"
        body = try values.decodeIfPresent(String.self, forKey: .body) ?? ""
        folder = try values.decodeIfPresent(String.self, forKey: .folder) ?? "Notes"
        tags = try values.decodeIfPresent([String].self, forKey: .tags) ?? []
        isPinned = try values.decodeIfPresent(Bool.self, forKey: .isPinned) ?? false
        isArchived = try values.decodeIfPresent(Bool.self, forKey: .isArchived) ?? false
        isDeleted = try values.decodeIfPresent(Bool.self, forKey: .isDeleted) ?? false
        createdAt = try values.decodeIfPresent(Date.self, forKey: .createdAt) ?? .now
        updatedAt = try values.decodeIfPresent(Date.self, forKey: .updatedAt) ?? .now
    }
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
    var folders: [String] { Array(Set(notes.filter { !$0.isDeleted }.map(\.folder))).sorted() }
    var visibleNotes: [FolioNote] {
        notes.filter { note in
            let location: Bool
            switch selectedFolder {
            case "Archive": location = note.isArchived && !note.isDeleted
            case "Recently Deleted": location = note.isDeleted
            case "All Notes": location = !note.isArchived && !note.isDeleted
            default: location = note.folder == selectedFolder && !note.isArchived && !note.isDeleted
            }
            let match = query.isEmpty || note.title.localizedCaseInsensitiveContains(query) ||
                note.body.localizedCaseInsensitiveContains(query) ||
                note.tags.contains(where: { $0.localizedCaseInsensitiveContains(query) })
            return location && match
        }.sorted { ($0.isPinned, $0.updatedAt) > ($1.isPinned, $1.updatedAt) }
    }
    var selectedNote: FolioNote? { notes.first { $0.id == selection } }
    func createNote(title: String = "Untitled Note") {
        let note = FolioNote(title: title, body: "")
        notes.insert(note, at: 0); selection = note.id; selectedFolder = "All Notes"; save()
    }
    func update(_ id: UUID, title: String, body: String, folder: String, tags: String) {
        guard let i = notes.firstIndex(where: { $0.id == id }) else { return }
        notes[i].title = title; notes[i].body = body
        notes[i].folder = folder.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Notes" : folder
        notes[i].tags = tags.split(separator: ",").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        notes[i].updatedAt = .now; save()
    }
    func togglePin(_ note: FolioNote) {
        guard let i = notes.firstIndex(where: { $0.id == note.id }) else { return }
        notes[i].isPinned.toggle(); notes[i].updatedAt = .now; save()
    }
    func togglePinSelected() { if let note = selectedNote { togglePin(note) } }
    func duplicate(_ note: FolioNote) {
        var copy = note
        copy.id = UUID()
        copy.title += " (Copy)"
        copy.isPinned = false
        copy.isArchived = false
        copy.isDeleted = false
        copy.createdAt = .now
        copy.updatedAt = .now
        notes.insert(copy, at: 0); selection = copy.id; selectedFolder = "All Notes"; save()
    }
    func createTemplate(_ kind: String) {
        let title: String
        let content: String
        switch kind {
        case "Class Notes":
            title = "Class Notes"
            content = "# Topic\n\n**Date:** \(Date.now.formatted(date: .abbreviated, time: .omitted))\n**Course:** \n\n## Key concepts\n- \n\n## Examples\n\n## Questions\n- [ ] "
        case "Meeting":
            title = "Meeting Notes"
            content = "# Meeting\n\n**Date:** \(Date.now.formatted(date: .abbreviated, time: .omitted))\n**Attendees:** \n\n## Agenda\n1. \n\n## Decisions\n- \n\n## Action items\n- [ ] Owner — Task — Due date"
        case "Daily Journal":
            title = "Daily Journal"
            content = "# Daily Journal\n\n**Date:** \(Date.now.formatted(date: .abbreviated, time: .omitted))\n\n## Today\n\n## Highlights\n- \n\n## To remember\n- "
        default:
            title = "Project Brief"
            content = "# Project Brief\n\n## Objective\n\n## Scope\n\n## Milestones\n- [ ] \n\n## Risks\n\n## Next steps"
        }
        let note = FolioNote(title: title, body: content)
        notes.insert(note, at: 0); selection = note.id; selectedFolder = "All Notes"; save()
    }
    func archive(_ note: FolioNote) {
        guard let i = notes.firstIndex(where: { $0.id == note.id }) else { return }
        notes[i].isArchived = true; notes[i].updatedAt = .now
        if selection == note.id { selection = nil }; save()
    }
    func moveToTrash(_ note: FolioNote) {
        guard let i = notes.firstIndex(where: { $0.id == note.id }) else { return }
        notes[i].isDeleted = true; notes[i].isArchived = false
        if selection == note.id { selection = nil }; save()
    }
    func restore(_ note: FolioNote) {
        guard let i = notes.firstIndex(where: { $0.id == note.id }) else { return }
        notes[i].isDeleted = false; notes[i].isArchived = false; notes[i].updatedAt = .now; save()
    }
    func permanentlyDelete(_ note: FolioNote) {
        notes.removeAll { $0.id == note.id }; if selection == note.id { selection = nil }; save()
    }
    private func load() {
        guard let data = try? Data(contentsOf: fileURL) else { return }
        notes = (try? JSONDecoder().decode([FolioNote].self, from: data)) ?? []
        selection = notes.first(where: { !$0.isDeleted && !$0.isArchived })?.id
    }
    private func save() {
        guard let data = try? JSONEncoder().encode(notes) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }
}

struct FolioRootView: View {
    @EnvironmentObject private var store: NoteStore
    private var sidebarItems: [String] { ["All Notes"] + store.folders + ["Archive", "Recently Deleted"] }
    var body: some View {
        NavigationSplitView {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("FOLIO").font(.system(size: 21, weight: .bold, design: .rounded))
                    Spacer()
                    Menu {
                        Button("Blank Note") { store.createNote() }
                        Divider()
                        ForEach(["Class Notes", "Meeting", "Daily Journal", "Project Brief"], id: \.self) { kind in
                            Button(kind) { store.createTemplate(kind) }
                        }
                    } label: { Image(systemName: "square.and.pencil") }.help("New note or template")
                }.padding(.bottom, 10)
                ForEach(sidebarItems, id: \.self) { item in
                    Button { store.selectedFolder = item } label: {
                        Label(item, systemImage: icon(for: item)).frame(maxWidth: .infinity, alignment: .leading)
                            .padding(8).background(store.selectedFolder == item ? Color.accentColor.opacity(0.14) : .clear, in: RoundedRectangle(cornerRadius: 8))
                    }.buttonStyle(.plain)
                }
                Spacer()
                Label("Stored on this Mac", systemImage: "internaldrive").font(.caption).foregroundStyle(.secondary)
            }.padding(14).navigationSplitViewColumnWidth(min: 175, ideal: 205)
        } content: {
            VStack(spacing: 10) {
                TextField("Search titles, text and tags", text: $store.query).textFieldStyle(.roundedBorder)
                List(selection: $store.selection) {
                    ForEach(store.visibleNotes) { note in
                        VStack(alignment: .leading, spacing: 5) {
                            HStack {
                                Text(note.title.isEmpty ? "Untitled Note" : note.title).font(.headline).lineLimit(1)
                                if note.isPinned { Image(systemName: "pin.fill").foregroundStyle(.orange) }
                            }
                            Text(note.body.isEmpty ? "No additional text" : note.body).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                            HStack {
                                Text(note.folder)
                                if !note.tags.isEmpty { Text("· " + note.tags.joined(separator: ", ")) }
                                Spacer(); Text(note.updatedAt, style: .date)
                            }.font(.caption2).foregroundStyle(.tertiary)
                        }.tag(note.id).contextMenu {
                            Button(note.isPinned ? "Unpin" : "Pin") { store.togglePin(note) }
                            Button("Duplicate") { store.duplicate(note) }
                            if note.isDeleted {
                                Button("Restore") { store.restore(note) }
                                Button("Delete Permanently", role: .destructive) { store.permanentlyDelete(note) }
                            } else {
                                Button("Archive") { store.archive(note) }
                                Button("Move to Recently Deleted", role: .destructive) { store.moveToTrash(note) }
                            }
                        }
                    }
                }.listStyle(.plain)
            }.padding(12).navigationSplitViewColumnWidth(min: 250, ideal: 300)
        } detail: {
            if let note = store.selectedNote {
                NoteEditor(note: note)
            } else {
                ContentUnavailableView("Select a note", systemImage: "note.text", description: Text("Create a note or choose one from the list."))
            }
        }
    }
    private func icon(for item: String) -> String {
        switch item { case "All Notes": return "note.text"; case "Archive": return "archivebox"; case "Recently Deleted": return "trash"; default: return "folder" }
    }
}

struct NoteEditor: View {
    @EnvironmentObject private var store: NoteStore
    let note: FolioNote
    @State private var title = ""
    @State private var bodyText = ""
    @State private var folder = "Notes"
    @State private var tags = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                TextField("Title", text: $title).font(.system(size: 28, weight: .bold)).textFieldStyle(.plain)
                Button { store.togglePin(note) } label: { Image(systemName: note.isPinned ? "pin.fill" : "pin") }.help("Pin note")
                ShareLink(item: "# \(title)\n\n\(bodyText)") { Image(systemName: "square.and.arrow.up") }.help("Share or export as Markdown")
                Menu {
                    Button("Archive") { store.archive(note) }
                    Button("Move to Recently Deleted", role: .destructive) { store.moveToTrash(note) }
                } label: { Image(systemName: "ellipsis.circle") }
            }
            HStack {
                TextField("Folder", text: $folder).textFieldStyle(.roundedBorder).frame(maxWidth: 180)
                TextField("Tags, separated by commas", text: $tags).textFieldStyle(.roundedBorder)
            }
            HStack(spacing: 8) {
                Button("H1") { bodyText += "\n# Heading\n" }
                Button("H2") { bodyText += "\n## Heading\n" }
                Button("Bullet") { bodyText += "\n- List item\n" }
                Button("Task") { bodyText += "\n- [ ] Task\n" }
                Button("Quote") { bodyText += "\n> Quote\n" }
                Spacer()
                Text("Markdown").font(.caption).foregroundStyle(.secondary)
            }.buttonStyle(.borderless)
            Divider()
            TextEditor(text: $bodyText).font(.system(size: 15)).scrollContentBackground(.hidden)
            HStack {
                Text("Autosaved locally"); Spacer()
                Text("\(bodyText.split(whereSeparator: \.isWhitespace).count) words")
            }.font(.caption).foregroundStyle(.secondary)
        }.padding(24)
            .onAppear { loadDraft() }
            .onChange(of: note.id) { _ in loadDraft() }
            .onChange(of: title) { _ in persist() }
            .onChange(of: bodyText) { _ in persist() }
            .onChange(of: folder) { _ in persist() }
            .onChange(of: tags) { _ in persist() }
    }
    private func loadDraft() { title = note.title; bodyText = note.body; folder = note.folder; tags = note.tags.joined(separator: ", ") }
    private func persist() { store.update(note.id, title: title, body: bodyText, folder: folder, tags: tags) }
}

struct SettingsView: View {
    @EnvironmentObject private var store: NoteStore
    var body: some View {
        Form {
            Section("Folio") {
                Text("Folio for macOS")
                Text("Local notes are stored in Application Support/Folio.").foregroundStyle(.secondary)
                Text("\(store.notes.count) notes stored locally")
            }
            Section("Available in this build") {
                Text("Folders, tags, full-text search, pinning, archive, recently deleted and autosave.")
            }
            Section("Still in development") {
                Text("Cloud sync, end-to-end encryption, biometric Safe Folder, rich-text blocks, attachments, PDF annotation, audio capture and handwriting.")
            }
        }.padding(20).frame(width: 460)
    }
}
