// Datos simulados que quedan mientras se conectan las ultimas pantallas del alumno:
// el perfil, la wallet y el detalle de inversion. Lo demas ya sale de la API.
class MockData {
  static final User currentUser = User(
    name: 'Elon Musk',
    level: 42,
    balance: 15400.50,
    totalInvestment: 8250.00,
    profileImageUrl: 'https://i.pravatar.cc/150?img=11',
    role: UserRole.student,
  );

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
