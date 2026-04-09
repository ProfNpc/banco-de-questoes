# QuestionBase — Sistema de Questões e Provas

Sistema Spring Boot para gerenciar banco de questões com **versionamento Git**, editor Markdown, suporte a imagens e geração de PDF.

## Tecnologias

| Camada | Tecnologia |
|--------|-----------|
| Backend | Java 21, Spring Boot 3.2, Spring Data JPA |
| Banco | H2 (arquivo) |
| Templates | Thymeleaf + Layout Dialect |
| Versionamento | JGit (Git embutido) |
| PDF | OpenPDF (fork do iText 2) |
| Markdown | CommonMark |
| Frontend | CSS puro, EasyMDE, SortableJS |

## Pré-requisitos

- Java 21+
- Maven 3.8+

## Executar

```bash
# Na raiz do projeto
mvn spring-boot:run
```

Acesse: **http://localhost:8080**

## Funcionalidades

### Questões
- Criar e editar questões **objetivas** (múltipla escolha) e **discursivas**
- Editor **Markdown** com prévia em tempo real (EasyMDE)
- **Upload de imagens** inseridas no meio do enunciado/alternativas via `![alt](url)`
- Marcar uma ou mais **alternativas corretas** (objetivas)
- Campos: título, disciplina, assunto, dificuldade
- Arrastar para reordenar alternativas

### Versionamento Git
- Cada criação/edição gera um **commit automático** no repositório Git local (`./git-questoes/`)
- Interface para **navegar pelo histórico** de versões de qualquer questão
- Visualização renderizada ou em Markdown bruto de versões antigas
- Nenhuma configuração adicional — usa JGit embutido

### Provas
- Montar provas selecionando questões do banco
- Definir pontuação por questão
- Reordenar questões via drag-and-drop

### Geração de PDF
- **Prova sem gabarito** — para aplicar a alunos
- **Prova com gabarito** — para o professor, marcando respostas corretas
- **Export avulso** — selecionar questões na listagem e gerar PDF diretamente
- Linhas para resposta em questões discursivas

## Estrutura do projeto

```
src/
├── main/
│   ├── java/com/questoes/
│   │   ├── config/        WebConfig (upload de arquivos)
│   │   ├── controller/    HomeController, QuestaoController, ProvaController
│   │   ├── dto/           QuestaoForm, ProvaForm
│   │   ├── entity/        Questao, Alternativa, Prova, ProvaQuestao
│   │   ├── repository/    JPA Repositories
│   │   └── service/       QuestaoService, ProvaService, GitService,
│   │                      PdfService, MarkdownService, FileStorageService
│   └── resources/
│       ├── static/css/    app.css
│       └── templates/     Thymeleaf (layout + questao/* + prova/*)
└── test/
```

## Dados persistidos

| Local | Conteúdo |
|-------|---------|
| `./data/questoesdb.*` | Banco H2 persistente |
| `./uploads/` | Imagens enviadas |
| `./git-questoes/` | Repositório Git com histórico de questões |

## Console H2

Disponível em: http://localhost:8080/h2-console  
JDBC URL: `jdbc:h2:file:./data/questoesdb`  
Usuário: `sa` / Senha: *(vazio)*
