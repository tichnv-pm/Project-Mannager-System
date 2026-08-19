import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ProjectWikiService, WikiPageResponse, WikiPageHistoryResponse } from './project-wiki.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';

export interface WikiNode {
  page: WikiPageResponse;
  children: WikiNode[];
  expanded: boolean;
}

@Component({
  selector: 'app-project-wiki',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    HasPermissionDirective,
    EmptyStateComponent
  ],
  templateUrl: './project-wiki.component.html',
  styleUrls: ['./project-wiki.component.scss']
})
export class ProjectWikiComponent implements OnInit {
  @Input() projectId!: string;

  private wikiService = inject(ProjectWikiService);
  private fb = inject(FormBuilder);

  loading = signal(true);
  error = signal<string | null>(null);
  pages = signal<WikiPageResponse[]>([]);
  treeNodes = signal<WikiNode[]>([]);

  // Selection
  selectedPage = signal<WikiPageResponse | null>(null);
  history = signal<WikiPageHistoryResponse[]>([]);
  historyLoading = signal(false);

  // Form Mode
  isEditMode = signal(false);
  isCreateMode = signal(false);
  formParentId: string | undefined = undefined;
  wikiForm: FormGroup;
  formSubmitting = signal(false);
  formError = signal<string | null>(null);

  constructor() {
    this.wikiForm = this.fb.group({
      title: ['', [Validators.required, Validators.maxLength(200)]],
      content: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    if (this.projectId) {
      this.loadWiki();
    }
  }

  loadWiki(): void {
    this.loading.set(true);
    this.error.set(null);
    this.wikiService.getWikiPages(this.projectId).subscribe({
      next: (res) => {
        this.pages.set(res);
        this.treeNodes.set(this.buildTree(res));
        this.loading.set(false);

        // Auto select first root page if available
        if (res.length > 0 && !this.selectedPage()) {
          const roots = this.treeNodes();
          if (roots.length > 0) {
            this.selectPage(roots[0].page);
          }
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message || 'Không thể tải Wiki dự án');
      }
    });
  }

  private buildTree(pages: WikiPageResponse[]): WikiNode[] {
    const nodeMap = new Map<string, WikiNode>();
    pages.forEach(p => {
      nodeMap.set(p.id, { page: p, children: [], expanded: true });
    });

    const roots: WikiNode[] = [];
    pages.forEach(p => {
      const node = nodeMap.get(p.id)!;
      if (p.parentPageId && nodeMap.has(p.parentPageId)) {
        nodeMap.get(p.parentPageId)!.children.push(node);
      } else {
        roots.push(node);
      }
    });

    return roots;
  }

  selectPage(page: WikiPageResponse): void {
    this.selectedPage.set(page);
    this.isEditMode.set(false);
    this.isCreateMode.set(false);
    this.loadHistory(page.id);
  }

  loadHistory(pageId: string): void {
    this.historyLoading.set(true);
    this.wikiService.getWikiPageHistory(pageId).subscribe({
      next: (res) => {
        this.history.set(res);
        this.historyLoading.set(false);
      },
      error: () => {
        this.historyLoading.set(false);
      }
    });
  }

  toggleExpand(node: WikiNode, event: MouseEvent): void {
    event.stopPropagation();
    node.expanded = !node.expanded;
  }

  // Actions
  initializeWiki(): void {
    this.loading.set(true);
    this.wikiService.initializeWiki(this.projectId).subscribe({
      next: () => {
        this.loadWiki();
      },
      error: (err) => {
        this.loading.set(false);
        alert(err.message || 'Khởi tạo Wiki thất bại');
      }
    });
  }

  openCreate(parentPageId?: string): void {
    this.isCreateMode.set(true);
    this.isEditMode.set(false);
    this.formParentId = parentPageId;
    this.formError.set(null);
    this.wikiForm.reset({
      title: '',
      content: ''
    });
  }

  openEdit(): void {
    const page = this.selectedPage();
    if (!page) return;
    this.isEditMode.set(true);
    this.isCreateMode.set(false);
    this.formError.set(null);
    this.wikiForm.patchValue({
      title: page.title,
      content: page.content
    });
  }

  cancelForm(): void {
    this.isCreateMode.set(false);
    this.isEditMode.set(false);
  }

  saveWiki(): void {
    if (this.wikiForm.invalid || this.formSubmitting()) {
      this.wikiForm.markAllAsTouched();
      return;
    }

    const val = this.wikiForm.value;
    this.formSubmitting.set(true);
    this.formError.set(null);

    if (this.isEditMode() && this.selectedPage()) {
      const page = this.selectedPage()!;
      this.wikiService.updateWikiPage(page.id, {
        title: val.title,
        content: val.content,
        version: page.version
      }).subscribe({
        next: (updated) => {
          this.formSubmitting.set(false);
          this.isEditMode.set(false);
          // Update local state
          const updatedPages = this.pages().map(p => p.id === updated.id ? updated : p);
          this.pages.set(updatedPages);
          this.treeNodes.set(this.buildTree(updatedPages));
          this.selectedPage.set(updated);
          this.loadHistory(updated.id);
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Cập nhật trang Wiki thất bại');
        }
      });
    } else if (this.isCreateMode()) {
      this.wikiService.createWikiPage(this.projectId, {
        parentPageId: this.formParentId,
        title: val.title,
        content: val.content
      }).subscribe({
        next: (created) => {
          this.formSubmitting.set(false);
          this.isCreateMode.set(false);
          // Add to local state
          const updatedPages = [...this.pages(), created];
          this.pages.set(updatedPages);
          this.treeNodes.set(this.buildTree(updatedPages));
          this.selectPage(created);
        },
        error: (err) => {
          this.formSubmitting.set(false);
          this.formError.set(err.message || 'Tạo trang Wiki thất bại');
        }
      });
    }
  }

  deleteWiki(): void {
    const page = this.selectedPage();
    if (!page) return;
    if (!confirm(`Bạn có chắc chắn muốn xóa trang wiki "${page.title}" cùng toàn bộ trang con?`)) return;

    this.wikiService.deleteWikiPage(page.id).subscribe({
      next: () => {
        const deletedIds = this.getAllChildIds(page.id);
        deletedIds.add(page.id);

        const updatedPages = this.pages().filter(p => !deletedIds.has(p.id));
        this.pages.set(updatedPages);
        this.treeNodes.set(this.buildTree(updatedPages));

        this.selectedPage.set(null);
        if (updatedPages.length > 0) {
          const roots = this.treeNodes();
          if (roots.length > 0) {
            this.selectPage(roots[0].page);
          }
        }
      },
      error: (err) => {
        alert(err.message || 'Xóa trang Wiki thất bại');
      }
    });
  }

  private getAllChildIds(parentId: string): Set<string> {
    const result = new Set<string>();
    const children = this.pages().filter(p => p.parentPageId === parentId);
    children.forEach(c => {
      result.add(c.id);
      this.getAllChildIds(c.id).forEach(id => result.add(id));
    });
    return result;
  }

  // Simple Markdown Parser
  renderMarkdown(md: string | undefined): string {
    if (!md) return '';
    let html = md
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    // Code blocks: ```code```
    html = html.replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>');

    // Inline code: `code`
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

    // Headers: # Header
    html = html.replace(/^# (.*$)/gim, '<h1>$1</h1>');
    html = html.replace(/^## (.*$)/gim, '<h2>$1</h2>');
    html = html.replace(/^### (.*$)/gim, '<h3>$1</h3>');

    // Bold: **text**
    html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

    // Bullet lists: * item or - item
    html = html.replace(/^\s*[\*\-]\s+(.*$)/gim, '<li>$1</li>');
    // Wrap consecutive <li> tags in <ul>
    html = html.replace(/(<li>[\s\S]*?<\/li>)/g, '<ul>$1</ul>');
    html = html.replace(/<\/ul>\s*<ul>/g, '');

    // Line breaks
    html = html.replace(/\n/g, '<br>');

    return html;
  }
}
