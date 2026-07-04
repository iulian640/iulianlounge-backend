```markdown
# iulianlounge-backend Development Patterns

> Auto-generated skill from repository analysis

## Overview
This skill teaches you the core development patterns and conventions used in the `iulianlounge-backend` TypeScript codebase. You'll learn about file naming, import/export styles, commit message conventions, and how to write and organize tests. This guide also provides step-by-step instructions for common workflows and suggested commands to streamline your development process.

## Coding Conventions

### File Naming
- Use **camelCase** for file names.
  - Example: `userService.ts`, `orderController.ts`

### Import Style
- Use **relative imports** for internal modules.
  - Example:
    ```typescript
    import { getUser } from './userService';
    ```

### Export Style
- Use **named exports** for functions, classes, and constants.
  - Example:
    ```typescript
    // userService.ts
    export function getUser(id: string) { ... }
    export const DEFAULT_ROLE = 'user';
    ```

### Commit Message Patterns
- Follow **Conventional Commits** with prefixes such as `ci`, `fix`, and `docs`.
  - Example:
    ```
    fix: correct typo in userService
    ci: update GitHub Actions workflow
    docs: add API usage instructions
    ```

## Workflows

### Commit Changes
**Trigger:** When making any code, configuration, or documentation update  
**Command:** `/commit`

1. Stage your changes:
    ```
    git add .
    ```
2. Write a conventional commit message using a valid prefix (`ci`, `fix`, `docs`):
    ```
    git commit -m "fix: resolve issue with login validation"
    ```
3. Push your changes:
    ```
    git push
    ```

### Add a New Module
**Trigger:** When creating a new feature or service  
**Command:** `/add-module`

1. Create a new file using camelCase naming, e.g., `orderService.ts`.
2. Use named exports for all functions and constants.
    ```typescript
    // orderService.ts
    export function createOrder(data: OrderData) { ... }
    ```
3. Import the module using a relative path where needed.
    ```typescript
    import { createOrder } from './orderService';
    ```

### Write and Run Tests
**Trigger:** When adding or updating functionality  
**Command:** `/test`

1. Create a test file with the pattern `*.test.*`, e.g., `userService.test.ts`.
2. Write tests using the project's preferred (unknown) testing framework.
    ```typescript
    // userService.test.ts
    import { getUser } from './userService';

    test('should return user by ID', () => {
      expect(getUser('123')).toEqual({ id: '123', name: 'Alice' });
    });
    ```
3. Run tests using the project's test runner (consult project documentation for exact command).

## Testing Patterns

- Test files are named with the pattern `*.test.*` (e.g., `userService.test.ts`).
- Tests are colocated with their respective modules or in a dedicated test directory.
- The specific testing framework is not detected; check project documentation or `package.json` for details.
- Example test structure:
    ```typescript
    // orderService.test.ts
    import { createOrder } from './orderService';

    test('creates a new order', () => {
      const order = createOrder({ item: 'Book', quantity: 1 });
      expect(order).toHaveProperty('id');
    });
    ```

## Commands
| Command      | Purpose                                             |
|--------------|-----------------------------------------------------|
| /commit      | Guide for making a conventional commit              |
| /add-module  | Steps to add a new module following conventions     |
| /test        | Instructions for writing and running tests          |
```
