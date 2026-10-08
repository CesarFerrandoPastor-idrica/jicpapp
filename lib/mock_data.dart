class MockData {
  static final User currentUser = User(
    name: 'Elon Musk',
    level: 42,
    balance: 15400.50,
    totalInvestment: 8250.00,
    profileImageUrl: 'https://i.pravatar.cc/150?img=11',
    role: UserRole.student,
  );

  static final User currentTeacher = User(
    name: 'Profesor X',
    level: 99,
    balance: 0.0,
    totalInvestment: 0.0,
    profileImageUrl: 'https://i.pravatar.cc/150?img=33',
    role: UserRole.teacher,
    surname: 'Charles',
    educationCenter: 'Instituto de Jóvenes Talentos',
  );

  static final List<StudentRanking> studentRankings = [
    StudentRanking(name: 'Juan Perez', investmentScore: 95, starsScore: 4.8),
    StudentRanking(name: 'Maria Garcia', investmentScore: 88, starsScore: 4.5),
    StudentRanking(name: 'Carlos Ruiz', investmentScore: 82, starsScore: 4.2),
    StudentRanking(name: 'Ana Martinez', investmentScore: 75, starsScore: 3.9),
  ];

  static final List<Project> featuredProjects = [
    Project(
      title: 'Eco-Drone Delivery',
      creator: 'Alice Smith',
      price: 150.0,
      description: 'Drones autónomos para entrega sostenible de paquetes en zonas urbanas densas, reduciendo la huella de carbono.',
      imageUrl: 'https://picsum.photos/seed/drone/400/300',
      category: 'Tecnología',
      initialInvestment: 1200.0,
      investmentBreakdown: 'Drones (800), Software (200), Licencias (200)',
      otherUsersInvestment: 450.0,
      performance: 12.5,
    ),
    Project(
      title: 'IA para Agricultura',
      creator: 'Bob Johnson',
      price: 320.5,
      description: 'Optimización de cultivos mediante algoritmos de IA que analizan la humedad y nutrientes del suelo en tiempo real.',
      imageUrl: 'https://picsum.photos/seed/agri/400/300',
      category: 'Salud',
      initialInvestment: 2500.0,
      investmentBreakdown: 'Sensores (1500), Desarrollo IA (1000)',
      otherUsersInvestment: 1200.0,
      performance: -2.4,
    ),
    Project(
      title: 'Plataforma EduTech 3.0',
      creator: 'Carlos Ruiz',
      price: 85.0,
      description: 'Una revolucionaria forma de aprender a programar a través de juegos interactivos y desafíos sociales.',
      imageUrl: 'https://picsum.photos/seed/edu/400/300',
      category: 'Educación',
      initialInvestment: 800.0,
      investmentBreakdown: 'Servidores (300), Diseño UI (500)',
      otherUsersInvestment: 150.0,
      performance: 25.8,
    ),
    Project(
      title: 'Coches Solares Urbanos',
      creator: 'Diana Prince',
      price: 540.0,
      description: 'Micro-vehículos propulsados por energía solar diseñados para el transporte eficiente en distancias cortas dentro de la ciudad.',
      imageUrl: 'https://picsum.photos/seed/car/400/300',
      category: 'Finanzas',
      initialInvestment: 5000.0,
      investmentBreakdown: 'Chasis (2000), Paneles Solares (2000), Motor (1000)',
      otherUsersInvestment: 3000.0,
      performance: 5.2,
    ),
    Project(
      title: 'Clean Water Initiative',
      creator: 'Evan Wright',
      price: 210.0,
      description: 'Sistemas de filtración de bajo coste para proporcionar agua potable segura en comunidades con recursos limitados.',
      imageUrl: 'https://picsum.photos/seed/water/400/300',
      category: 'Salud',
      initialInvestment: 1500.0,
      investmentBreakdown: 'Filtros (1000), Logística (500)',
      otherUsersInvestment: 800.0,
      performance: 8.1,
    ),
  ];

  static final List<Course> myCourses = [
    Course(
      title: 'Creación de una Empresa',
      description: 'Aprende los pasos fundamentales para transformar una idea en un negocio viable y escalable.',
      imageUrl: 'https://picsum.photos/seed/business/400/300',
      progress: 0.8,
    ),
    Course(
      title: 'Inversiones y Mercados',
      description: 'Descubre cómo funciona el mercado de valores y las diferentes estrategias de inversión.',
      imageUrl: 'https://picsum.photos/seed/stocks/400/300',
      progress: 0.4,
    ),
    Course(
      title: 'Economía de Startups',
      description: 'Análisis de modelos de negocio disruptivos y rondas de financiación.',
      imageUrl: 'https://picsum.photos/seed/startup/400/300',
      progress: 0.1,
    ),
    Course(
      title: 'Marketing Digital Pro',
      description: 'Estrategias avanzadas de adquisición y retención de clientes en la era digital.',
      imageUrl: 'https://picsum.photos/seed/marketing/400/300',
      progress: 0.0,
    ),
  ];

  static final List<Comment> projectComments = [
    Comment(user: 'Juan Perez', text: '¡Increíble proyecto! Veo mucho potencial aquí.', time: 'Hace 2h'),
    Comment(user: 'Maria Garcia', text: 'Me gusta el enfoque en la sostenibilidad.', time: 'Hace 5h'),
    Comment(user: 'Carlos Lopez', text: '¿Tienen pensado expandirse a otros países?', time: 'Ayer'),
    Comment(user: 'Ana Martinez', text: 'La tecnología detrás de esto es muy sólida.', time: 'Hace 3d'),
  ];

  static final List<WalletTransaction> walletTransactions = [
    WalletTransaction(title: 'Inversión: Eco-Drone Delivery', amount: -150.0, date: '29 Abr 2026', type: 'Inversión'),
    WalletTransaction(title: 'Creación: Mi StartUp FinTech', amount: -2000.0, date: '25 Abr 2026', type: 'Creación'),
    WalletTransaction(title: 'Recarga de Balance', amount: 5000.0, date: '20 Abr 2026', type: 'Recarga'),
    WalletTransaction(title: 'Venta Acciones: IA Agricultura', amount: 850.0, date: '15 Abr 2026', type: 'Venta'),
  ];

  static final List<InvestmentTransaction> investmentTransactions = [
    InvestmentTransaction(title: 'Compra: 5 acciones Eco-Drone', amount: -750.0, shares: 5, date: '29 Abr 2026', type: 'Compra'),
    InvestmentTransaction(title: 'Venta: 10 acciones IA Agricultura', amount: 3205.0, shares: 10, date: '15 Abr 2026', type: 'Venta'),
    InvestmentTransaction(title: 'Compra: 2 acciones Coches Solares', amount: -1080.0, shares: 2, date: '10 Abr 2026', type: 'Compra'),
  ];
}

class User {
  final String name;
  final int level;
  final double balance;
  final double totalInvestment;
  final String profileImageUrl;
  final UserRole role;
  final String? surname;
  final String? educationCenter;

  User({
    required this.name,
    required this.level,
    required this.balance,
    required this.totalInvestment,
    required this.profileImageUrl,
    required this.role,
    this.surname,
    this.educationCenter,
  });
}

enum UserRole { student, teacher }

class StudentRanking {
  final String name;
  final double investmentScore;
  final double starsScore;

  StudentRanking({
    required this.name,
    required this.investmentScore,
    required this.starsScore,
  });
}

class Project {
  final String title;
  final String creator;
  final double price;
  final String description;
  final String imageUrl;
  final String category;
  final double initialInvestment;
  final String investmentBreakdown;
  final double otherUsersInvestment;
  final double performance;

  Project({
    required this.title,
    required this.creator,
    required this.price,
    required this.description,
    required this.imageUrl,
    required this.category,
    required this.initialInvestment,
    required this.investmentBreakdown,
    required this.otherUsersInvestment,
    required this.performance,
  });

  double get totalInvestment => initialInvestment + otherUsersInvestment;
}

class Course {
  final String title;
  final String description;
  final String imageUrl;
  final double progress;

  Course({
    required this.title,
    required this.description,
    required this.imageUrl,
    required this.progress,
  });
}

class Comment {
  final String user;
  final String text;
  final String time;

  Comment({
    required this.user,
    required this.text,
    required this.time,
  });
}

class WalletTransaction {
  final String title;
  final double amount;
  final String date;
  final String type;

  WalletTransaction({
    required this.title,
    required this.amount,
    required this.date,
    required this.type,
  });
}

class InvestmentTransaction {
  final String title;
  final double amount;
  final double shares;
  final String date;
  final String type;

  InvestmentTransaction({
    required this.title,
    required this.amount,
    required this.shares,
    required this.date,
    required this.type,
  });
}
