import { Directive, Input, TemplateRef, ViewContainerRef, inject, EffectRef, effect } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';

@Directive({
  selector: '[hasPermission]',
  standalone: true
})
export class HasPermissionDirective {
  private authService = inject(AuthService);
  private templateRef = inject(TemplateRef<unknown>);
  private viewContainer = inject(ViewContainerRef);

  private permissionCode = '';
  private isHidden = true;

  @Input() set hasPermission(permission: string) {
    this.permissionCode = permission;
    this.updateView();
  }

  constructor() {
    effect(() => {
      // Trigger update on user change
      const user = this.authService.currentUserSignal();
      this.updateView();
    });
  }

  private updateView(): void {
    if (!this.permissionCode) return;

    const hasAccess = this.authService.hasPermission(this.permissionCode);
    if (hasAccess && this.isHidden) {
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.isHidden = false;
    } else if (!hasAccess && !this.isHidden) {
      this.viewContainer.clear();
      this.isHidden = true;
    }
  }
}
