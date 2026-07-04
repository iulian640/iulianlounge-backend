```markdown
# iulianlounge-backend Development Patterns

> Auto-generated skill from repository analysis

## Overview
This skill teaches the core development patterns and conventions used in the `iulianlounge-backend` TypeScript repository. It covers file naming, import/export styles, commit message conventions, and testing patterns. By following these guidelines, contributors can maintain consistency and quality across the codebase.

## Coding Conventions

### File Naming
- **Style:** camelCase
- **Example:**  
  ```plaintext
  userController.ts
  orderService.ts
  ```

### Import Style
- **Style:** Relative imports
- **Example:**  
  ```typescript
  import { getUser } from './userService';
  import { Order } from '../models/order';
  ```

### Export Style
- **Style:** Named exports
- **Example:**  
  ```typescript
  // userService.ts
  export function getUser(id: string) { /* ... */ }

  // order.ts
  export interface Order { /* ... */ }
  ```

### Commit Message Convention
- **Type:** Conventional Commits
- **Prefix Example:**  
  ```
  docs: update README with setup instructions
  ```
- **Average Length:** ~59 characters

## Workflows

### Documenting Changes
**Trigger:** When updating documentation or comments  
**Command:** `/docs-update`

1. Make your documentation changes in the relevant files.
2. Use a conventional commit message with the `docs` prefix.
   ```
   docs: improve API usage examples in README
   ```
3. Push your changes to the repository.

## Testing Patterns

- **Test File Pattern:** `*.test.*` (e.g., `userService.test.ts`)
- **Testing Framework:** Not explicitly detected; follow standard TypeScript test practices.
- **Example Test File:**
  ```typescript
  // userService.test.ts
  import { getUser } from './userService';

  describe('getUser', () => {
    it('should return a user object', () => {
      const user = getUser('123');
      expect(user).toBeDefined();
    });
  });
  ```

## Commands
| Command      | Purpose                                         |
|--------------|-------------------------------------------------|
| /docs-update | Use when updating documentation or comments      |
```
