export default function Footer() {
  return (
    <footer className="mt-auto bg-gray-50 border-t border-gray-200">
      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <p className="text-center text-sm text-gray-500">
          &copy; {new Date().getFullYear()} Shopizer. All rights reserved.
        </p>
      </div>
    </footer>
  );
}
