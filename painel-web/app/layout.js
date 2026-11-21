import "./globals.css";

export const metadata = {
  title: "Piper · Painel",
  description: "Painel da Piper, sua secretária pessoal",
};

export default function RootLayout({ children }) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
